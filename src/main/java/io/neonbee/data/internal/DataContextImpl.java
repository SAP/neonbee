package io.neonbee.data.internal;

import static com.google.common.collect.Iterators.unmodifiableIterator;
import static io.neonbee.internal.Helper.hostIp;
import static io.neonbee.internal.Helper.mutableCopyOf;
import static io.neonbee.internal.handler.CorrelationIdHandler.CORRELATION_ID;

import java.time.LocalTime;
import java.time.ZoneId;
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Streams;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataException;
import io.neonbee.internal.handler.CorrelationIdHandler;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

public class DataContextImpl implements DataContext {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final String USER_PRINCIPAL = "userPrincipal";

    private static final String BEARER_TOKEN = "bearerToken";

    private static final String DATA = "data";

    private static final String PATH = "path";

    private static final Pattern BEARER_AUTHENTICATION_PATTERN = Pattern.compile("Bearer\\s(.+)");

    private final String correlationId;

    private final String bearerToken;

    private final JsonObject userPrincipal;

    private Map<String, Object> data;

    private Deque<DataVerticleCoordinate> pathStack;

    public DataContextImpl() {
        // initialize an empty context (w/ will also create an empty path stack)
        this(null, null, null, null, null);
    }

    public DataContextImpl(RoutingContext routingContext) {
        this(CorrelationIdHandler.getCorrelationId(routingContext),
                Optional.ofNullable(routingContext.request().getHeader(HttpHeaders.AUTHORIZATION))
                        .map(BEARER_AUTHENTICATION_PATTERN::matcher).filter(Matcher::matches)
                        .map(matcher -> matcher.group(1)).orElse(null),
                Optional.ofNullable(routingContext.user()).map(User::principal).orElse(null), null, null);
    }

    public DataContextImpl(String correlationId, JsonObject userPrincipal) {
        this(correlationId, null, userPrincipal, null, null);
    }

    public DataContextImpl(String correlationId, String bearerToken, JsonObject userPrincipal,
            Map<String, Object> data) {
        this(correlationId, bearerToken, userPrincipal, data, null);
    }

    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public DataContextImpl(String correlationId, String bearerToken, JsonObject userPrincipal, Map<String, Object> data,
            Deque<DataVerticleCoordinate> paths) {
        this.correlationId = correlationId;
        this.bearerToken = bearerToken;
        // actually create a read only user principal object, so that no one can tamper with the data
        this.userPrincipal = Optional.ofNullable(userPrincipal).map(JsonObject::getMap)
                .map(Collections::unmodifiableMap).map(JsonObject::new).orElse(null);
        this.setData(data); // create a mutable copy of the map
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
        if (value instanceof Map) {
            value = new JsonObject((Map) value);
        } else if (value instanceof List) {
            value = new JsonArray((List) value);
        }
        return (T) value;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes", "TypeParameterUnusedInFormals" })
    public <T> T remove(String key) {
        Object value = data().remove(key);
        if (value instanceof Map) {
            value = new JsonObject((Map) value);
        } else if (value instanceof List) {
            value = new JsonArray((List) value);
        }
        return (T) value;
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
        return new JsonObject().put(CORRELATION_ID, context.correlationId()).put(BEARER_TOKEN, context.bearerToken())
                .put(USER_PRINCIPAL, context.userPrincipal()).put(DATA, context.data())
                .put(PATH, pathToJson(context.path())).toString();
    }

    private static JsonArray pathToJson(Iterator<DataVerticleCoordinate> path) {
        return new JsonArray(streamPath(path).map(JsonObject::mapFrom).collect(Collectors.toList()));
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
        return new DataContextImpl(contextJson.getString(CORRELATION_ID), contextJson.getString(BEARER_TOKEN),
                contextJson.getJsonObject(USER_PRINCIPAL),
                Optional.ofNullable(contextJson.getJsonObject(DATA)).map(JsonObject::getMap).orElse(null),
                Optional.ofNullable(contextJson.getJsonArray(PATH)).map(DataContextImpl::pathFromJson).orElse(null));
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
            coordinate.setIpAddress(hostIp());
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

    @VisibleForTesting
    static class DataVerticleCoordinateImpl implements DataVerticleCoordinate {
        private final String qualifiedName;

        private final String requestTimestamp;

        private String deploymentId;

        private String ipAddress;

        private String responseTimestamp;

        @JsonCreator
        DataVerticleCoordinateImpl(@JsonProperty("qualifiedName") String qualifiedName) {
            this.qualifiedName = qualifiedName;
            this.requestTimestamp = LocalTime.now(ZoneId.systemDefault()).toString();
        }

        @Override
        public String getRequestTimestamp() {
            return requestTimestamp;
        }

        @Override
        public String getResponseTimestamp() {
            return responseTimestamp;
        }

        @Override
        public String getQualifiedName() {
            return qualifiedName;
        }

        @Override
        public String getIpAddress() {
            return ipAddress;
        }

        public DataVerticleCoordinateImpl updateResponseTimestamp() {
            this.responseTimestamp = LocalTime.now(ZoneId.systemDefault()).toString();
            return this;
        }

        public DataVerticleCoordinateImpl setDeploymentId(String instanceId) {
            this.deploymentId = instanceId;
            return this;
        }

        public DataVerticleCoordinateImpl setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        @Override
        public String getDeploymentId() {
            return deploymentId;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            if (requestTimestamp != null) {
                builder.append(requestTimestamp).append(' ');
            }
            builder.append(qualifiedName);
            if (deploymentId != null) {
                builder.append('[').append(deploymentId).append(']');
            }
            if (ipAddress != null) {
                builder.append('@').append(ipAddress);
            }
            if (responseTimestamp != null) {
                builder.append(' ').append(responseTimestamp);
            }
            return builder.toString();
        }
    }
}
