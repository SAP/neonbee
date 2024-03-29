package io.neonbee.data;

import static io.neonbee.data.DataAction.READ;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import io.neonbee.internal.codec.BufferDeserializer;
import io.neonbee.internal.codec.BufferSerializer;
import io.neonbee.internal.helper.CollectionHelper;
import io.vertx.core.buffer.Buffer;

/**
 * Note that DataQuery is always mutable, as a copy of it will be created when sent via the event bus.
 */
public final class DataQuery { // NOPMD not a "god class"
    private static final Pattern QUERY_SPLIT_PATTERN = Pattern.compile("&");

    private static final Pattern PARAM_SPLIT_PATTERN = Pattern.compile("=");

    private static final int MAX_CONTENT_LENGTH_TO_STRING = 100;

    @VisibleForTesting
    @JsonProperty
    DataAction action = READ;

    @VisibleForTesting
    @JsonProperty
    String uriPath;

    @VisibleForTesting
    @JsonProperty
    Map<String, List<String>> parameters;

    @VisibleForTesting
    @JsonProperty
    Map<String, List<String>> headers;

    @VisibleForTesting
    @JsonSerialize(using = BufferSerializer.class)
    @JsonDeserialize(using = BufferDeserializer.class)
    @JsonProperty
    Buffer body;

    /**
     * New DataQuery.
     */
    public DataQuery() {
        this(READ);
    }

    /**
     * DataQuery with just an action to perform.
     *
     * @param action The action to perform
     */
    public DataQuery(DataAction action) {
        this(action, (String) null);
    }

    /**
     * DataQuery with just an action to perform /w a body.
     *
     * @param action The action to perform
     * @param body   The body of this query
     */
    public DataQuery(DataAction action, Buffer body) {
        this(action, null, body);
    }

    /**
     * DataQuery with just a URI path component.
     *
     * @param uriPath The URI path to request
     */
    public DataQuery(String uriPath) {
        this(READ, uriPath);
    }

    /**
     * DataQuery with a URI path component and an action to perform.
     *
     * @param action  The action to perform
     * @param uriPath The URI path to request
     */
    public DataQuery(DataAction action, String uriPath) {
        this(action, uriPath, (Buffer) null);
    }

    /**
     * DataQuery with a URI path component and an action to perform /w a body.
     *
     * @param action  The action to perform
     * @param uriPath The URI path to request
     * @param body    The body of this query
     */
    public DataQuery(DataAction action, String uriPath, Buffer body) {
        this(action, uriPath, null, null, body);
    }

    /**
     * DataQuery with a URI path, query and headers.
     *
     * @param action          The action to perform
     * @param uriPath         The URI path to request
     * @param queryParameters The query parameters of this query
     * @param headers         The headers of this query
     * @param body            The body of this query
     */
    public DataQuery(DataAction action, String uriPath, Map<String, List<String>> queryParameters,
            Map<String, List<String>> headers, Buffer body) {
        this.action = action;
        this.setUriPath(uriPath); // Calling the setter here, because there is an additional check implemented.
        this.headers = CollectionHelper.mapToCaseInsensitiveTreeMap(headers);
        this.parameters = CollectionHelper.mutableCopyOf(queryParameters != null ? queryParameters : Map.of());
        this.body = CollectionHelper.copyOf(body);
    }

    /**
     * Returns the {@link DataAction} of this data query.
     *
     * @return the action
     */
    public DataAction getAction() {
        return action;
    }

    /**
     * Set the {@link DataAction} for this query.
     *
     * @param action the action to set
     * @return the DataQuery for chaining
     */
    public DataQuery setAction(DataAction action) {
        this.action = action;
        return this;
    }

    /**
     * Returns the uriPath of this data query.
     *
     * @return the uriPath
     */
    public String getUriPath() {
        return uriPath;
    }

    /**
     * Set the uri path for this query.
     *
     * @param uriPath the uriPath to set
     * @return the DataQuery for chaining
     */
    public DataQuery setUriPath(String uriPath) {
        if (!Strings.isNullOrEmpty(uriPath) && uriPath.contains("?")) {
            throw new IllegalArgumentException("uriPath must not contain a query");
        }

        this.uriPath = uriPath;
        return this;
    }

    /**
     * Returns the parameters of this data query represented as a URL encoded string.
     *
     * @return the URL encoded query string
     */
    public String getRawQuery() {
        return getQuery(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    private String getQuery(Function<String, String> encoder) {
        Function<String, Stream<String>> paramBuilder = name -> getParameterValues(name).stream()
                .map(value -> String.format("%s=%s", encoder.apply(name), encoder.apply(value)));

        return parameters.keySet().stream().flatMap(paramBuilder).collect(joining("&"));
    }

    /**
     * Set the query string. Please note that this will also remove all previously added parameters.
     *
     * @param encodedQuery the (URL encoded) query to set
     * @return the DataQuery for chaining
     * @throws IllegalArgumentException if the implementation encounters illegal characters
     */
    public DataQuery setRawQuery(String encodedQuery) {
        this.parameters = parseEncodedQueryString(encodedQuery);
        return this;
    }

    /**
     * Returns the parameters of this data query.
     *
     * @return the parameters as Map
     */
    public Map<String, List<String>> getParameters() {
        return parameters;
    }

    /**
     * Returns a list containing all parameter values for a given parameter.
     *
     * @param name The name of the parameter
     * @return All values for a given parameter
     */
    public List<String> getParameterValues(String name) {
        return getParameters().get(name);
    }

    /**
     * Returns the first value for a given parameter.
     *
     * @param name The name of the query parameter
     * @return The value for a given query parameter, or null if no parameter was found
     */
    public String getParameter(String name) {
        return getParameter(name, null);
    }

    /**
     * Like {@link #getParameter(String)} but specifying a default value to return if there is no entry.
     *
     * @param name         The name of the query parameter
     * @param defaultValue the default value to use if the query parameter is not present
     * @return The value for a given query parameter or {@code defaultValue} if parameter is not present
     */
    public String getParameter(String name, String defaultValue) {
        return Stream.ofNullable(getParameterValues(name))
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(defaultValue);
    }

    /**
     * Adds one or multiple values for a given parameter.
     *
     * @param name   The name of the query parameter
     * @param values The values for the given query parameter, leave it empty if only the parameter name should be
     *               stored
     * @return the DataQuery for chaining
     */
    public DataQuery addParameter(String name, String... values) {
        getParameters().computeIfAbsent(name, s -> new ArrayList<>()).addAll(Arrays.asList(values));
        return this;
    }

    /**
     * Sets one or multiple values for a given parameter.
     *
     * @param name   The name of the query parameter
     * @param values The values for the given query parameter, leave it empty if only the parameter name should be
     *               stored
     * @return the DataQuery for chaining
     */
    public DataQuery setParameter(String name, String... values) {
        removeParameter(name).addParameter(name, values);
        return this;
    }

    /**
     * Removes the parameter with the given name.
     *
     * @param name the name of the parameter to remove
     * @return the DataQuery for chaining
     */
    public DataQuery removeParameter(String name) {
        getParameters().remove(name);
        return this;
    }

    /**
     * Pars the encoded query parameter string.
     *
     * @param encodedQuery the encoded query parameter string
     * @return the query parameter map
     * @throws IllegalArgumentException if the implementation encounters illegal characters
     */
    public static Map<String, List<String>> parseEncodedQueryString(String encodedQuery) {
        return parseQueryString(encodedQuery, s -> URLDecoder.decode(s, StandardCharsets.UTF_8));
    }

    private static Map<String, List<String>> parseQueryString(String encodedQuery, Function<String, String> decoder) {
        if (Strings.isNullOrEmpty(encodedQuery)) {
            return new HashMap<>();
        }

        return QUERY_SPLIT_PATTERN.splitAsStream(encodedQuery)
                .map(paramString -> PARAM_SPLIT_PATTERN.split(paramString, 2))
                .map(paramArray -> Map.entry(decoder.apply(paramArray[0]),
                        paramArray.length > 1 ? decoder.apply(paramArray[1]) : ""))
                .collect(groupingBy(Map.Entry::getKey, HashMap::new,
                        mapping(Map.Entry::getValue, toCollection(ArrayList::new))));
    }

    /**
     * Returns the query headers.
     *
     * @return the headers
     */
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    /**
     * A list of headers with a given name.
     *
     * @param name The name of the header
     * @return A list of values for this header
     */
    public List<String> getHeaderValues(String name) {
        return headers.get(name);
    }

    /**
     * Returns a specific header (if multiple, the first found).
     *
     * @param name The name of the header
     * @return The header or null
     */
    public String getHeader(String name) {
        return Stream.ofNullable(getHeaderValues(name))
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Sets the headers for the query.
     *
     * @param headers the headers to set
     * @return the DataQuery for chaining
     */
    public DataQuery setHeaders(Map<String, List<String>> headers) {
        this.headers = CollectionHelper.mapToCaseInsensitiveTreeMap(headers);
        return this;
    }

    /**
     * Adds a header to the query.
     *
     * @param name  the name of the header
     * @param value the value of the header
     * @return the DataQuery for chaining
     */
    public DataQuery addHeader(String name, String value) {
        this.headers.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
        return this;
    }

    /**
     * Sets a specific header for the query.
     *
     * @param name  the name of the header
     * @param value the value of the header
     * @return the DataQuery for chaining
     */
    public DataQuery setHeader(String name, String value) {
        this.headers.put(name, new ArrayList<>(Collections.singleton(value)));
        return this;
    }

    /**
     * Removes the header with the given name.
     *
     * @param name the name of the header to remove
     * @return the DataQuery for chaining
     */
    public DataQuery removeHeader(String name) {
        this.headers.remove(name);
        return this;
    }

    /**
     * Returns the body of the query.
     *
     * @return the body
     */
    public Buffer getBody() {
        return body;
    }

    /**
     * Sets the query body.
     *
     * @param body the body to set
     * @return the DataQuery for chaining
     */
    public DataQuery setBody(Buffer body) {
        this.body = CollectionHelper.copyOf(body);
        return this;
    }

    /**
     * Copy a DataQuery (decided to not go for a copy constructor as brace handling can easily be messed up).
     *
     * @return a copy of this DataQuery
     */
    public DataQuery copy() {
        return new DataQuery(action, uriPath, parameters, headers, body);
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        } else if (!(object instanceof DataQuery)) {
            return false;
        }

        DataQuery dataQuery = (DataQuery) object;
        return Objects.equals(action, dataQuery.action) && Objects.equals(uriPath, dataQuery.uriPath)
                && Objects.equals(parameters, dataQuery.parameters) && Objects.equals(headers, dataQuery.headers)
                && Objects.equals(body, dataQuery.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, uriPath, parameters, headers, body);
    }

    private String trimContent(String content) {
        if (Objects.isNull(content)) {
            return "";
        } else if (content.length() <= MAX_CONTENT_LENGTH_TO_STRING) {
            return content;
        } else {
            return content.substring(0, MAX_CONTENT_LENGTH_TO_STRING) + "...";
        }
    }

    @Override
    public String toString() {
        return "DataQuery [action=" + action + ", uriPath=" + uriPath + ", query=" + ", parameters=" + parameters
                + ", headers=" + headers.keySet().stream()
                        .map(key -> key + "="
                                + headers.get(key).stream().map(this::trimContent).toList())
                        .collect(joining(", ", "{", "}"))
                + ", body=" + body + ']';
    }
}
