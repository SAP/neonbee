package io.neonbee.test.base;

import static io.neonbee.internal.helper.StringHelper.EMPTY;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.olingo.commons.api.edm.FullQualifiedName;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;

/**
 * This class can be used to construct an ODataRequest based on the provided full qualified name and the
 * {@link HttpMethod}. Additionally, the request can be extended by specifying OData key predicates, entity properties,
 * headers, and query parameters,
 */
public class ODataRequest extends AbstractODataRequest<ODataRequest> {

    private boolean metadata;

    private boolean count;

    private Map<String, String> keys = new HashMap<>();

    private String property;

    private String systemQueryExpand;

    /**
     * Constructs an {@link ODataRequest}.
     *
     * @param entity The full qualified name which corresponds to the entity which will be requested
     */
    public ODataRequest(FullQualifiedName entity) {
        super(entity);
    }

    @Override
    protected ODataRequest self() {
        return this;
    }

    /**
     * Sets the method for the OData request. By default the HTTP method will be GET.
     *
     * @param method {@link HttpMethod} to set
     * @return An {@link ODataRequest} which considers the new HTTP method when sending the request
     */
    @Override
    public ODataRequest setMethod(HttpMethod method) {
        return super.setMethod(method);
    }

    /**
     * Sets the body which will be used when sending the request.
     *
     * @param body A {@link Buffer} with body of the request
     * @return An {@link ODataRequest} which considers the body when sending the request
     */
    public ODataRequest setBody(Buffer body) {
        this.body = body;
        return this;
    }

    /**
     * Appends {@code /$count} to the resource path of the URL, identifying an entity set or collection. As per OData
     * specification, the {@code /$count} suffix can be used to address the raw value of the number of items in a
     * collection and should not be combined with system query options ($top, $skip, $orderby, $expand, and $format).
     * Please be aware of this when setting query options with {@link #setQuery(Map)}.<br>
     * <br>
     * <strong>Example:</strong> Given a {@link FullQualifiedName fqn}
     * {@code FullQualifiedName("my-namespace/my-entity")}:
     *
     * <pre>
     * oDataRequest.setCount() -&gt; "my-namespace/my-entity/$count"
     * </pre>
     *
     * @return An {@link ODataRequest} which considers the {@code /$count} suffix when building the request
     */
    public ODataRequest setCount() {
        this.count = true;
        return this;
    }

    /**
     * Sets the passed value as a single-part key of the key property value. The type of the passed key is
     * {@link String}, which requires surrounding single quotes for the key, as specified by OData Version 4.0. These
     * quotes will be added to the request uri and should not be part of the passed argument. Note that if this method
     * is called multiple times in a row, only the key which was set in the last invocation will be added to the
     * request.<br>
     * <br>
     * <strong>Example:</strong> Given a {@link FullQualifiedName fqn}
     * {@code FullQualifiedName("my-namespace/my-entity")}:
     *
     * <pre>
     * oDataRequest.setKey("3") -&gt; "my-namespace/my-entity('3')"
     * </pre>
     *
     * @param id The entity key which specifies a single-part key of type {@link String}
     * @return An {@link ODataRequest} with the canonical key predicate of the passed key
     */
    public ODataRequest setKey(String id) {
        setKey(Map.of("", id));
        return this;
    }

    /**
     * Sets the passed value as a single-part key of the key property value. The type of the passed key is {@link Long}.
     * Note that if this method is called multiple times in a row, only the key which was set in the last invocation
     * will be added to the request.<br>
     * <br>
     * <strong>Example:</strong> Given a {@link FullQualifiedName fqn}
     * {@code FullQualifiedName("my-namespace/my-entity")}:
     *
     * <pre>
     * oDataRequest.setKey(3L) -&gt; "my-namespace/my-entity(3)"
     * </pre>
     *
     * @param id The entity key which specifies a single-part key of type {@link Long}
     * @return An {@link ODataRequest} which contains the passed key
     */
    public ODataRequest setKey(long id) {
        setKey(Map.of("", id));
        return this;
    }

    /**
     * Sets the passed value as a single-part key of the key property value. The type of the passed key is
     * {@link LocalDate}. Note that if this method is called multiple times in a row, only the key which was set in the
     * last invocation will be added to the request.<br>
     * <br>
     * <strong>Example:</strong> Given a {@link FullQualifiedName fqn}
     * {@code FullQualifiedName("my-namespace/my-entity")}:
     *
     * <pre>
     * oDataRequest.setKey(LocalDate.of(2020, 2, 22)) -&gt; "my-namespace/my-entity(2020-02-22)"
     * </pre>
     *
     * @param id The entity key which specifies a single-part key of type {@link LocalDate}
     * @return An {@link ODataRequest} which contains the passed key
     */
    public ODataRequest setKey(LocalDate id) {
        setKey(Map.of("", id));
        return this;
    }

    /**
     * Sets the full key predicate for multiple keys of an entity which are passed via a Map of {@link String} to
     * {@link Object}. The type of the passed key must be either {@link String}, {@link Long}, or {@link LocalDate}.
     * Note that if this method is called multiple times in a row, only the key which was set in the last invocation
     * will be added to the request.<br>
     * <br>
     * <strong>Example:</strong> Given a {@link FullQualifiedName fqn}
     * {@code FullQualifiedName("my-namespace/my-entity")}:
     *
     * <pre>
     * oDataRequest.setKey(Map.of("ID", 3)) -&gt; "my-namespace/my-entity(ID=3)"
     * oDataRequest.setKey(Map.of("ID", 3, "Name", "Hodor")) -&gt; "my-namespace/my-entity(ID=3,Name='Hodor')"
     * </pre>
     *
     * @param compositeKey A map which contains the name of a key and its value
     * @return An {@link ODataRequest} which contains the passed key
     */
    public ODataRequest setKey(Map<String, Object> compositeKey) {
        Function<Object, String> convert = obj -> {
            if (obj instanceof String) {
                return "'" + obj + "'";
            } else if (obj instanceof Long) {
                return obj.toString();
            } else if (obj instanceof LocalDate) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                return ((LocalDate) obj).format(formatter);
            } else {
                throw new IllegalArgumentException("Expecting either type String or Long as key, but received "
                        + obj.getClass().getCanonicalName());
            }
        };

        this.keys = compositeKey.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> convert.apply(entry.getValue())));
        return this;
    }

    /**
     * Sets a specific property of an entity. Note that if this method is called multiple times in a row, only the key
     * which was set in the last invocation will be added to the request.<br>
     * <br>
     * <strong>Example:</strong> Given a {@link FullQualifiedName fqn}
     * {@code FullQualifiedName("my-namespace/my-entity")}:
     *
     * <pre>
     * oDataRequest.setKey(1).setProperty("my-property") -&gt; "my-namespace/my-entity(1)/my-property"
     * oDataRequest.setProperty("my-property") -&gt; "my-namespace/my-entity"  // will be ignored.
     * </pre>
     *
     * @param propertyName The name of the property
     * @return An {@link ODataRequest} which contains the passed property
     */
    public ODataRequest setProperty(String propertyName) {
        this.property = propertyName;
        return this;
    }

    /**
     * Adds the passed query parameter name and value as decoded query parameter on the {@link ODataRequest}.
     *
     * @param key   The name of the query parameter
     * @param value The value of the query parameter
     * @return An {@link ODataRequest} which contains the passed query parameter
     */
    public ODataRequest addQueryParam(String key, String value) {
        this.query.add(key, value);
        return this;
    }

    /**
     * Sets the passed map of strings as decoded query parameters on the {@link ODataRequest}. If this method is called
     * multiple times, the passed query parameters of the last call will be used.
     *
     * @param query A map of decoded query parameters which will be added to request
     * @return An {@link ODataRequest} which contains the passed query options
     */
    public ODataRequest setQuery(Map<String, String> query) {
        setQuery(Objects.isNull(query) ? null : MultiMap.caseInsensitiveMultiMap().addAll(query)); // NOPMD
        return this;
    }

    /**
     * Sets the passed map of strings as decoded query parameters on the {@link ODataRequest}. If this method is called
     * multiple times, the passed query parameters of the last call will be used.
     *
     * @param query A {@link MultiMap} of decoded query parameters which will be added to request
     * @return An {@link ODataRequest} which contains the passed header
     */
    public ODataRequest setQuery(MultiMap query) {
        this.query = Optional.ofNullable(query).orElse(MultiMap.caseInsensitiveMultiMap());
        return this;
    }

    /**
     * Configure the system query expand with the passed value. The expandValue "Products" would result into
     * "expand=Products".
     *
     * @param expandValue the value for the expand query
     * @return An {@link ODataRequest} with the passed expand query
     */
    public ODataRequest setExpandQuery(String expandValue) {
        systemQueryExpand = "expand=" + expandValue;
        return this;
    }

    /**
     * Builds and returns the OData URL of the {@link ODataRequest} based on namespace, entity name, key predicates,
     * properties, and OData suffixes {@code $metadata}, {@code $count}.
     *
     * @return The full URL path of the {@link ODataRequest}
     */
    @Override
    protected String getUri() {
        String namespace = getUriNamespacePath();

        if (metadata) {
            return namespace + "$metadata";
        }

        String entitySet = namespace + entity.getName();
        if (count) {
            return entitySet + "/$count";
        }
        return entitySet + getPredicate(keys) + (Strings.isNullOrEmpty(property) ? EMPTY : '/' + property)
                + (Strings.isNullOrEmpty(systemQueryExpand) ? EMPTY : "?$" + systemQueryExpand);
    }

    private String getPredicate(Map<String, String> keyMap) throws IllegalArgumentException {
        if (keyMap.isEmpty()) {
            return EMPTY;
        }

        Function<String, String> decorateParentheses = s -> '(' + s + ')';

        if (keyMap.size() == 1) {
            return decorateParentheses.apply(keyMap.values().iterator().next());
        }

        if (keys.containsKey(EMPTY)) {
            throw new IllegalArgumentException("For multi-part keys the full key predicate is required.");
        }

        return decorateParentheses.apply(Joiner.on(',').withKeyValueSeparator('=').join(keys.entrySet()));
    }
}
