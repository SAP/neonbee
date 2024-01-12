package io.neonbee.data.internal;

import static com.google.common.collect.Iterators.unmodifiableIterator;
import static io.neonbee.internal.handler.CorrelationIdHandler.CORRELATION_ID;
import static io.neonbee.internal.helper.CollectionHelper.isNullOrEmpty;
import static io.neonbee.internal.helper.CollectionHelper.mutableCopyOf;
import static io.neonbee.internal.helper.HostHelper.getHostIp;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Streams;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataException;
import io.neonbee.data.DataRequest;
import io.neonbee.internal.handler.CorrelationIdHandler;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;

@SuppressWarnings("PMD.GodClass")
public class DataContextImpl implements DataContext {
    @VisibleForTesting
    static final String NO_SESSION_ID_AVAILABLE_KEY = "noSessionIdAvailable";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final String USER_PRINCIPAL_KEY = "userPrincipal";

    private static final String BEARER_TOKEN_KEY = "bearerToken";

    private static final String DATA_KEY = "data";

    private static final String PATH_KEY = "path";

    private static final String SESSION_ID_KEY = "sessionId";

    private static final Pattern BEARER_AUTHENTICATION_PATTERN = Pattern.compile("Bearer\\s(.+)");

    private static final String RESPONSE_METADATA_KEY = "responsedata";

    private final String correlationId;

    private final String bearerToken;

    private final JsonObject userPrincipal;

    private final String sessionId;

    private Map<String, Object> data;

    private Map<String, Object> responseData;

    private Deque<DataVerticleCoordinate> pathStack;

    /**
     * This is a map between {@link DataRequest} to an invoked verticle and the received response data for the request.
     * This map will not be propagated to the upstream verticles by default.
     */
    private Map<DataRequest, Map<String, Object>> receivedData;

    public DataContextImpl() {
        // initialize an empty context (w/ will also create an empty path stack)
        this(null, null, null, null, null, null);
    }

    public DataContextImpl(RoutingContext routingContext) {
        this(CorrelationIdHandler.getCorrelationId(routingContext),
                Optional.ofNullable(routingContext.session()).map(Session::id).orElse(NO_SESSION_ID_AVAILABLE_KEY),
                Optional.ofNullable(routingContext.request().getHeader(HttpHeaders.AUTHORIZATION))
                        .map(BEARER_AUTHENTICATION_PATTERN::matcher).filter(Matcher::matches)
                        .map(matcher -> matcher.group(1)).orElse(null),
                Optional.ofNullable(routingContext.user()).map(User::principal).orElse(null), null, null);
    }

    public DataContextImpl(String correlationId, String sessionId, JsonObject userPrincipal) {
        this(correlationId, sessionId, null, userPrincipal, null, null);
    }

    public DataContextImpl(String correlationId, String sessionId, String bearerToken, JsonObject userPrincipal,
            Map<String, Object> data) {
        this(correlationId, sessionId, bearerToken, userPrincipal, data, null);
    }

    @SuppressWarnings({ "PMD.ConstructorCallsOverridableMethod", "ChainingConstructorIgnoresParameter",
            "PMD.UnusedFormalParameter" })
    public DataContextImpl(String correlationId, String sessionId, String bearerToken, JsonObject userPrincipal,
            Map<String, Object> data, Deque<DataVerticleCoordinate> paths) {
        this(correlationId, sessionId, bearerToken, userPrincipal, data, null, paths);
    }

    @SuppressWarnings({ "PMD.ConstructorCallsOverridableMethod" })
    public DataContextImpl(String correlationId, String sessionId, String bearerToken, JsonObject userPrincipal,
            Map<String, Object> data, Map<String, Object> responseData, Deque<DataVerticleCoordinate> paths) {
        this.correlationId = correlationId;
        this.sessionId = sessionId;
        this.bearerToken = bearerToken;
        // actually create a read only user principal object, so that no one can tamper with the data
        this.userPrincipal = Optional.ofNullable(userPrincipal).map(JsonObject::getMap)
                .map(Collections::unmodifiableMap).map(JsonObject::new).orElse(null);
        this.setData(data); // create a mutable copy of the map
        this.responseData = mutableCopyOf(responseData);
        this.setPath(paths); // create a mutable copy of the dequeue
    }

    /**
     * Copy constructor, use {@code context.copy()}.
     *
     * @param original The original data context
     */
    @VisibleForTesting
    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    protected DataContextImpl(DataContext original) {
        this.correlationId = original.correlationId();
        this.sessionId = original.sessionId();
        this.bearerToken = original.bearerToken();
        this.userPrincipal = original.userPrincipal();
        this.setData(original.data());
        this.setPath(original.path());
    }

    @Override
    public String correlationId() {
        return correlationId;
    }

    @Override
    public String bearerToken() {
        return bearerToken;
    }

    @Override
    public JsonObject userPrincipal() {
        return userPrincipal;
    }

    @Override
    public Map<String, Object> data() {
        if (this.data == null) {
            this.data = new HashMap<>();
        }
        return this.data;
    }

    /**
     * Sets the invocation path of the context.
     *
     * @param path an iterator of coordinates
     */
    @VisibleForTesting
    protected void setPath(Iterator<DataVerticleCoordinate> path) {
        this.pathStack =
                streamPath(path).collect(Collector.of(ArrayDeque::new, (deq, t) -> deq.addFirst(t), (d1, d2) -> {
                    d2.addAll(d1);
                    return d2;
                }));
    }

    /**
     * Sets the invocation path of the context.
     *
     * @param path a deque of coordinates
     * @return current context
     */
    @VisibleForTesting
    protected DataContext setPath(Deque<DataVerticleCoordinate> path) {
        this.pathStack = mutableCopyOf(path, ArrayDeque::new);
        return this;
    }

    @Override
    @SuppressWarnings("PMD.NullAssignment")
    public final DataContext setData(Map<String, Object> data) {
        this.data = (data != null) && !data.isEmpty() ? mutableCopyOf(data) : null;
        return this;
    }

    @Override
    public DataContext mergeData(Map<String, Object> data) {
        if ((data != null) && !data.isEmpty()) {
            // instead of putAll, might be worth it to write a more sophisticated logic using .merge()
            this.data.putAll(mutableCopyOf(data));
        }
        return this;
    }

    @Override
    public DataContext put(String key, Object value) {
        this.data().put(key, value);
        return this;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes", "TypeParameterUnusedInFormals" })
    public <T> T get(String key) {
        Object value = data().get(key);
        if (value instanceof Map map) {
            value = new JsonObject(map);
        } else if (value instanceof List list) {
            value = new JsonArray(list);
        }
        return (T) value;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes", "TypeParameterUnusedInFormals" })
    public <T> T remove(String key) {
        Object value = data().remove(key);
        if (value instanceof Map map) {
            value = new JsonObject(map);
        } else if (value instanceof List list) {
            value = new JsonArray(list);
        }
        return (T) value;
    }

    @Override
    public Map<String, Object> responseData() {
        if (this.responseData == null) {
            this.responseData = new HashMap<>();
        }
        return this.responseData;
    }

    @Override
    public DataContext mergeResponseData(Map<String, Object> data) {
        if (!isNullOrEmpty(data)) {
            // instead of putAll, might be worth it to write a more sophisticated logic using .merge()
            this.responseData().putAll(mutableCopyOf(data));
        }
        return this;
    }

    @Override
    public Map<DataRequest, Map<String, Object>> receivedData() {
        return this.receivedData;
    }

    @Override
    public DataContext setReceivedData(Map<DataRequest, Map<String, Object>> map) {
        this.receivedData = Collections.unmodifiableMap(map);
        return this;
    }

    @Override
    public Map<String, Object> findReceivedData(DataRequest dataRequest) {
        return this.receivedData.getOrDefault(dataRequest, Map.of());
    }

    @Override
    public Optional<Map<String, Object>> findFirstReceivedData(String qualifiedName) {
        return this.receivedData.entrySet().stream()
                .filter(entry -> qualifiedName.equals(entry.getKey().getQualifiedName())).findFirst()
                .map(Map.Entry::getValue);
    }

    @Override
    public List<Map<String, Object>> findAllReceivedData(String qualifiedName) {
        return this.receivedData.entrySet().stream()
                .filter(entry -> qualifiedName.equals(entry.getKey().getQualifiedName())).map(Map.Entry::getValue)
                .toList();
    }

    @Override
    public void propagateReceivedData() {
        receivedData.values().stream().forEach(data -> this.mergeResponseData(data));
    }

    /**
     * Encodes a given {@link DataContext} to string.
     *
     * @param context A data context to encode
     * @return The passed data context represented as string
     */
    public static String encodeContextToString(DataContext context) {
        if (context == null) {
            // actually it's fine for the context to be null, so also a null should be set as header
            return null;
        }
        return new JsonObject().put(CORRELATION_ID, context.correlationId()).put(SESSION_ID_KEY, context.sessionId())
                .put(BEARER_TOKEN_KEY, context.bearerToken()).put(USER_PRINCIPAL_KEY, context.userPrincipal())
                .put(DATA_KEY, new JsonObject(context.data()))
                .put(RESPONSE_METADATA_KEY, new JsonObject(context.responseData()))
                .put(PATH_KEY, pathToJson(context.path())).toString();
    }

    private static JsonArray pathToJson(Iterator<DataVerticleCoordinate> path) {
        return new JsonArray(streamPath(path).map(JsonObject::mapFrom).toList());
    }

    /**
     * Decodes a given string to {@link DataContext}.
     *
     * @param contextString A string to decode
     * @return a new {@link DataContext} instance representing the passed string
     */
    public static DataContext decodeContextFromString(String contextString) {
        if (contextString == null) {
            // in case the header value is null, also return null from this deserialization
            return null;
        }

        JsonObject contextJson = new JsonObject(contextString);
        return new DataContextImpl(contextJson.getString(CORRELATION_ID), contextJson.getString(SESSION_ID_KEY),
                contextJson.getString(BEARER_TOKEN_KEY), contextJson.getJsonObject(USER_PRINCIPAL_KEY),
                Optional.ofNullable(contextJson.getJsonObject(DATA_KEY)).map(JsonObject::getMap).orElse(null),
                Optional.ofNullable(contextJson.getJsonObject(RESPONSE_METADATA_KEY)).map(JsonObject::getMap)
                        .orElse(null),
                Optional.ofNullable(contextJson.getJsonArray(PATH_KEY)).map(DataContextImpl::pathFromJson)
                        .orElse(null));
    }

    private static Deque<DataVerticleCoordinate> pathFromJson(JsonArray array) {
        return array.stream().map(JsonObject.class::cast).map(object -> object.mapTo(DataVerticleCoordinateImpl.class))
                .collect(ArrayDeque::new, Deque::push, Deque::addAll);
    }

    /**
     * Push a new verticle into the stack.
     *
     * @param name verticle name
     */
    public void pushVerticleToPath(String name) {
        if (!pathStack.isEmpty()) {
            DataVerticleCoordinate topVerticle = pathStack.peek();
            if (name.equalsIgnoreCase(topVerticle.getQualifiedName())) {
                LOGGER.error("A DataVerticle {} is sending message to itself, which could lead to a dead loop", name);
                throw new DataException(String.format("DataVerticle %s is sending message to itself.", name));
            }
        }

        pathStack.push(new DataVerticleCoordinateImpl(name));
    }

    /**
     * Complement the missing data of the top coordinate on the stack.
     *
     * @param deploymentId deployment id of the target verticle
     * @return current context
     */
    public DataContext amendTopVerticleCoordinate(String deploymentId) {
        Optional.ofNullable(pathStack.peek()).map(DataVerticleCoordinateImpl.class::cast).ifPresent(coordinate -> {
            coordinate.setDeploymentId(deploymentId);
            coordinate.setIpAddress(getHostIp());
        });
        return this;
    }

    /**
     * Remove the top coordinate from the stack.
     */
    public void popVerticleFromPath() {
        pathStack.pop();
    }

    @Override
    public Iterator<DataVerticleCoordinate> path() {
        return unmodifiableIterator(pathStack.descendingIterator());
    }

    /**
     * Return the current invocation path as string.
     *
     * @return the current invocation path as string.
     */
    @Override
    public String pathAsString() {
        return streamPath(path()).map(DataVerticleCoordinate::toString)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    /**
     * @return the session as String
     */
    @Override
    public String sessionId() {
        return sessionId;
    }

    static Stream<DataVerticleCoordinate> streamPath(Iterator<DataVerticleCoordinate> path) {
        return Optional.ofNullable(path).map(Streams::stream).orElseGet(Stream::empty);
    }

    @Override
    public DataContextImpl copy() {
        return new DataContextImpl(this);
    }

    @Override
    public void updateResponseTimestamp() {
        Optional.ofNullable(pathStack.peek()).map(DataVerticleCoordinateImpl.class::cast)
                .ifPresent(DataVerticleCoordinateImpl::updateResponseTimestamp);
    }
}
