package io.neonbee.test.base;

import static io.neonbee.endpoint.odatav4.ODataV4Endpoint.DEFAULT_BASE_PATH;
import static io.neonbee.internal.helper.StringHelper.EMPTY;
import static io.neonbee.test.base.NeonBeeTestBase.readServerConfig;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.olingo.commons.api.edm.FullQualifiedName;

import com.google.common.base.Strings;

import io.neonbee.NeonBee;
import io.neonbee.config.EndpointConfig;
import io.neonbee.endpoint.odatav4.ODataV4Endpoint;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

/**
 * Abstract base class for OData requests.
 *
 * @param <T> the type of OData request to wrap
 */
public abstract class AbstractODataRequest<T extends AbstractODataRequest<T>> {
    protected final FullQualifiedName entity;

    protected HttpMethod method = HttpMethod.GET;

    protected MultiMap headers = MultiMap.caseInsensitiveMultiMap();

    protected MultiMap query = MultiMap.caseInsensitiveMultiMap();

    protected Buffer body;

    protected Consumer<HttpRequest<Buffer>> interceptor;

    public AbstractODataRequest(FullQualifiedName entity) {
        this.entity = entity;
    }

    protected abstract T self();

    /**
     * Sets the method for the OData request. By default, the HTTP method will be GET.
     *
     * @param method {@link HttpMethod} to set
     * @return An {@link ODataRequest} which considers the new HTTP method when sending the request
     */
    protected T setMethod(HttpMethod method) {
        this.method = Optional.ofNullable(method).orElse(HttpMethod.GET);
        return self();
    }

    /**
     * Configure the OData request to add the passed HTTP header and its corresponding value.
     *
     * @param key   The name of the header
     * @param value The value of the header
     * @return An OData request which contains the passed header
     */
    public T addHeader(String key, String value) {
        this.headers.add(key, value);
        return self();
    }

    /**
     * Sets the passed HTTP headers and their corresponding values to the OData request. If this method is called
     * multiple times, the passed headers of the last call will be used.
     *
     * @param headers A {@link Map} which maps HTTP header names to their corresponding value
     * @return An OData request which contains the passed header
     */
    public T setHeaders(Map<String, String> headers) {
        setHeaders(Objects.isNull(headers) ? null : MultiMap.caseInsensitiveMultiMap().addAll(headers)); // NOPMD
        return self();
    }

    /**
     * Sets the passed HTTP headers and their corresponding values to the OData request. If this method is called
     * multiple times, the passed headers of the last call will be used.
     *
     * @param headers A {@link MultiMap} which maps HTTP header names to their corresponding value
     * @return An OData request which contains the passed header
     */
    public T setHeaders(MultiMap headers) {
        this.headers = Optional.ofNullable(headers).orElse(MultiMap.caseInsensitiveMultiMap());
        return self();
    }

    /**
     * Get the URI of the OData request.
     *
     * @return request URI path addressing the desired OData service endpoint. This path segment gets merged with the
     *         OData endpoint's base path before being dispatched via {@link #send(NeonBee)}.
     */
    protected abstract String getUri();

    /**
     * Get the namespace part of the URI of the OData request.
     *
     * @return {@link FullQualifiedName#getNamespace()} of this request's {@link #entity} followed by a forward slash
     *         (/) or an empty string if the entity's namespace is {@code null} or empty.
     */
    protected String getUriNamespacePath() {
        return Strings.isNullOrEmpty(entity.getNamespace()) ? EMPTY : entity.getNamespace() + '/';
    }

    /**
     * Allows to modify the HTTP request. This can only be done once and is typically done at the end of building the
     * request. If this method is called multiple times, the passed interceptor of the last call will be used.
     *
     * @param interceptor The passed interceptor
     * @return An OData request with the raw request
     */
    public T interceptRequest(Consumer<HttpRequest<Buffer>> interceptor) {
        this.interceptor = interceptor;
        return self();
    }

    /**
     * Constructs an {@link HttpRequest} based on the underlying OData request and sends it.
     *
     * @param neonBee The current {@link NeonBee} instance of this test that is used to create an HTTP request with the
     *                {@link WebClient}
     * @return A {@link Future} with the {@link HttpResponse} which is received from sending the OData request.
     */
    public Future<HttpResponse<Buffer>> send(NeonBee neonBee) {
        Vertx vertx = neonBee.getVertx();
        return readServerConfig(neonBee).compose(config -> {
            int port = config.getPort();

            String basePath = config.getEndpointConfigs().stream()
                    .filter(eConfig -> ODataV4Endpoint.class.getSimpleName().equals(eConfig.getType())).findFirst()
                    .map(EndpointConfig::getBasePath).orElse(DEFAULT_BASE_PATH);

            WebClientOptions clientOpts = new WebClientOptions().setDefaultHost("localhost").setDefaultPort(port);
            HttpRequest<Buffer> httpRequest = WebClient.create(vertx, clientOpts).request(method, basePath + getUri());

            httpRequest.putHeaders(headers);
            httpRequest.queryParams().addAll(query);
            httpRequest.queryParams(); // ensures that query params are encoded
            Optional.ofNullable(interceptor).ifPresent(i -> i.accept(httpRequest));

            return Optional.ofNullable(body).map(httpRequest::sendBuffer).orElse(httpRequest.send());
        });
    }
}
