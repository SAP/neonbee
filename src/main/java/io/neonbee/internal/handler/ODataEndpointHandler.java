package io.neonbee.internal.handler;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static io.neonbee.entity.EntityModelManager.EVENT_BUS_MODELS_LOADED_ADDRESS;
import static io.neonbee.entity.EntityModelManager.getSharedModels;
import static io.neonbee.internal.Helper.EMPTY;
import static io.neonbee.internal.Helper.entryConsumer;
import static io.neonbee.internal.Helper.entryFunction;
import static io.neonbee.internal.Helper.inputStreamToBuffer;
import static io.neonbee.internal.Helper.replaceLast;
import static io.vertx.core.Future.succeededFuture;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static org.apache.olingo.server.core.ODataHandlerException.MessageKeys.AMBIGUOUS_XHTTP_METHOD;
import static org.apache.olingo.server.core.ODataHandlerException.MessageKeys.HTTP_METHOD_NOT_ALLOWED;
import static org.apache.olingo.server.core.ODataHandlerException.MessageKeys.INVALID_HTTP_METHOD;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import org.apache.olingo.commons.api.ex.ODataRuntimeException;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataHandler;
import org.apache.olingo.server.api.ODataLibraryException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.core.ODataHandlerException;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.entity.EntityModel;
import io.neonbee.internal.Helper.BufferInputStream;
import io.neonbee.internal.SharedDataAccessor;
import io.neonbee.internal.processor.BatchProcessor;
import io.neonbee.internal.processor.EntityCollectionProcessor;
import io.neonbee.internal.processor.EntityProcessor;
import io.neonbee.internal.processor.PrimitiveProcessor;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public final class ODataEndpointHandler implements Handler<RoutingContext> {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private final ServiceMetadata serviceMetadata;

    /**
     * Either STRICT (&lt;namespace&gt;.&lt;service&gt;), LOOSE (&lt;path4 mapping of namespace&gt;-&lt;path4 mapping of
     * service&gt;) or CDS (&lt;path4 mapping of service&gt;) URI mapping:
     * <p>
     * - STRICT: is a one-to-one case-sensitive mapping of the namespace and service name to the URI of the OData
     * endpoint. For example my.very.CatalogService is mapped to the URI like "/odata/my.very.CatalogService/EntitySet"
     * where there are some simple rules to follow: /odata/ is the base path of the endpoint (to be customized in the
     * ServerVerticle configuration), everything from there to the last slash of the URI path is the service namespace
     * and name (so my.very.CatalogService), which itself the last occurring dot (.) separates the service namespace
     * (my.very) from the service name (CatalogService), everything after the last slash of the URI path is considered
     * to be the entity set name.
     * <p>
     * - CDS: is a condensed, human-readable mapping of only the service name to the URI of the OData endpoint, with
     * some limitations, due to the nature of being a non-uniform function based on the following CDS rule set:
     * <ol>
     * <li>remove the service namespace (my.very.CatalogService -&gt; CatalogService)
     * <li>delete "Service" if it is being the last word of the string (case-sensitive, CatalogService -&gt; Catalog)
     * <li>lower case the first character of the service name (Catalog -&gt; catalog)
     * <li>prefix a - to all upper case letters, followed by a lower case and lowercase the first (fooBar -&gt; foo-bar)
     * <li>lowercase the rest of the string (FOO -&gt; foo)
     * <li>replace all occurences of "_" with "-" (foo_bar -&gt; foo-bar)
     * </ol>
     * Due to the namespace being removed from the URI, no one-to-one mapping from URI to OData service is possible when
     * choosing CDS mapping. To still be able to address all OData services uniquely, the following condition must be
     * met: All services in different namespaces must not share the same mapped URI representation. So for example
     * my.very.CatalogService maps to /odata/catalog/EntitySet in the URI. So does my.other.CatalogService or also
     * my.third.Catalog. In this the entities accessible are non-deterministic and could be either services of any of
     * the provided namespaces (my.very, my.other or my.third). So this must be prevented by choosing different service
     * names for each of the three services, e.g. my.very.CatalogAService (maps to /odata/catalog-a/),
     * my.other.CatalogBService (maps to /odata/catalog-b/) and my.third.Catalog_C (maps to /odata/catalog-c/).
     * <p>
     * For the CDS mapping having the service name "Service", can be considered as a special case. In case a service is
     * named solely "Service", again regardless of its service namespace, it is mapped to an empty path segment, so to
     * the OData root URI /odata/. This behavior can be utilized, e.g. in case only one OData service should be exposed
     * on the OData root and no separate service name mappings are needed.
     * <p>
     * - LOOSE: is a condensed, human-readable mapping of the namespace and service name to the URI of the OData
     * endpoint, with some limitations, due to the nature of being a non-uniform transformation function. The function
     * will first capitalize the namespace and prepend it to the service name (so my.very.CatalogService maps to
     * MyVeryCatalogService). Afterwards the CDS transformation is applied and the URI is mapped to my-very-catalog in
     * the example. As in contrast to the CDS mapping, the LOOSE URI mapping will still take the namespace into
     * consideration, most services can be uniquely mapped and addressed via the endpoint. However, similar to the CDS
     * limitation, not any loaded model must share the same URI representation, in order to still be accessible. For
     * example my.very.CatalogService and my.very.catalog.Service do share the same URI representation, which would
     * cause one of the services to be inaccessible!
     */
    @SuppressWarnings("checkstyle:JavadocVariable")
    public enum UriConversion implements UnaryOperator<String> {
        STRICT(uriPart -> uriPart), CDS(uriPart -> {
            uriPart = uriPart.substring(uriPart.lastIndexOf('.') + 1); // my.very.CatalogService -> CatalogService
            uriPart = uriPart.replaceFirst("Service$", EMPTY); // CatalogService -> Catalog
            uriPart = Pattern.compile("^[A-Z]+").matcher(uriPart) // XMLService -> xmlService
                    .replaceFirst(match -> match.group().toLowerCase(Locale.ROOT));
            uriPart = Pattern.compile("[A-Z]+[a-z0-9]").matcher(uriPart) // FooBarX9 --> foo-bar-x9
                    .replaceAll(match -> '-' + match.group().toLowerCase(Locale.ROOT));
            uriPart = uriPart.toLowerCase(Locale.ROOT); // FOO -> foo
            uriPart = uriPart.replace('_', '-'); // foo_bar -> foo-bar

            return uriPart;
        }), LOOSE(uriPart -> {
            // join the namespace and service name, my.very.CatalogService -> my-very-catalog
            uriPart = CDS.apply(uriPart.substring(0, uriPart.lastIndexOf('.') + 1).replace('.', '-'))
                    + CDS.apply(uriPart);
            uriPart = uriPart.replaceFirst("-$", EMPTY); // service name was "Service", thus namespace ends with a -

            return uriPart;
        });

        @SuppressWarnings("ImmutableEnumChecker") // Because we can't make UnaryOperator immutable
        final UnaryOperator<String> convert;

        UriConversion(UnaryOperator<String> convert) {
            this.convert = convert;
        }

        @Override
        public String apply(String uriPart) {
            if (isNullOrEmpty(uriPart)) {
                return EMPTY;
            }

            return convert.apply(uriPart);
        }

        /**
         * Parses a given string and returns the related UriConversion.
         *
         * @param name UriConversion represented as string
         * @return the UriConversion, or STRICT in case String doesn't match a UriConversion.
         */
        public static UriConversion byName(String name) {
            switch (nullToEmpty(name).toLowerCase(Locale.ROOT)) {
            case "strict":
                return STRICT;
            case "loose":
                return LOOSE;
            case "cds":
                return CDS;
            default:
                LOGGER.warn("Unknown URI conversion {} falling back to \"strict\" mapping", name);
                return STRICT;
            }
        }
    }

    /**
     * Creates a router that contains a route for every loaded OData model.
     *
     * @param vertx         the related Vert.x instance
     * @param basePath      the base path of the router
     * @param uriConversion the UriConversion
     * @return the generated router
     */
    public static Router router(Vertx vertx, String basePath, UriConversion uriConversion) {
        Router router = Router.router(vertx);
        AtomicBoolean initialized = new AtomicBoolean(); // true if the router was initialized already
        AtomicReference<Map<String, EntityModel>> models = new AtomicReference<>();

        // Register the event bus consumer first, otherwise it could happen that during initialization we are missing an
        // update to the data model, a refresh of the router will only be triggered in case it is already initialized.
        // This is a NON-local consumer, this means the reload could be triggered from anywhere, however currently the
        // reload is only triggered in case the EntityModelManager reloads the models locally (and triggers a local
        // publish of the message, thus only triggering the routers to be reloaded on the local instance).
        vertx.eventBus().consumer(EVENT_BUS_MODELS_LOADED_ADDRESS, message -> {
            // do not refresh the router if it wasn't even initialized
            if (initialized.get()) {
                refreshRouter(vertx, router, basePath, uriConversion, models);
            }
        });

        // This router is initialized lazy, this means that all required routes will be populated on first request to
        // said router, not before. This comes with three major advantages: a) it makes the initialization code more
        // easy, as in case we'd first register to the event handler, we'd always have to check if the model is loaded
        // already and potentially have to deal with a race condition b) no model is loaded / routes are initialized if
        // when NeonBee is started and / or in case the endpoint is not used.
        Route initialRoute = router.route();
        initialRoute.handler(
                routingContext -> new SharedDataAccessor(vertx, ODataEndpointHandler.class).getLocalLock(asyncLock ->
                // immediately initialize the router, this will also "arm" the event bus listener
                (!initialized.getAndSet(true) ? refreshRouter(vertx, router, basePath, uriConversion, models)
                        : succeededFuture()).onComplete(handler -> {
                            // Wait for the refresh to finish (the result doesn't matter), remove the initial route, as
                            // this will redirect all requests to the registered service endpoint handlers (if non have
                            // been registered, e.g. due to a failure in model loading, it'll result in an 404). Could
                            // have been removed already by refreshRouter, we don't care!
                            initialRoute.remove();
                            if (asyncLock.succeeded()) {
                                // releasing the lock will cause other requests unblock and not call the initial route
                                asyncLock.result().release();
                            }

                            // Let the router again handle the context again, now with either all service endpoints
                            // registered, or none in case there have been a failure while loading the models.
                            // NOTE: Re-route is the only elegant way I found to restart the current router to take
                            // the new routes! Might consider checking again with the Vert.x 4.0 release.
                            routingContext.reroute(routingContext.request().uri());
                        })));

        return router;
    }

    private static Future<Void> refreshRouter(Vertx vertx, Router router, String basePath, UriConversion uriConversion,
            AtomicReference<Map<String, EntityModel>> currentModels) {
        return getSharedModels(vertx).compose(models -> {
            if (models == currentModels.get()) {
                return succeededFuture(); // no update needed
            } else {
                currentModels.set(models);
            }

            // before adding new routes, get a list of existing routes, to remove after the new routes have been added
            List<Route> existingRoutes = router.getRoutes();

            // Register new routes first, this will avoid downtimes of already existing services. Register the shortest
            // routes last, this will lead to some routes like the empty namespace / to be registered last.
            models.values().stream().flatMap(entityModel -> entityModel.getEdmxes().entrySet().stream())
                    .map(entryFunction(
                            (schemaNamespace, edmxModel) -> Map.entry(uriConversion.apply(schemaNamespace), edmxModel)))
                    .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(String::length).reversed()))
                    .forEach(entryConsumer((uriPath, edmxModel) -> {
                        router.route((uriPath.isEmpty() ? EMPTY : ("/" + uriPath)) + "/*").handler(create(edmxModel));
                        LOGGER.info("Serving OData service endpoint for {} at {}{} ({} URI mapping)",
                                edmxModel.getEdm().getEntityContainer().getNamespace(), basePath, uriPath,
                                uriConversion.name().toLowerCase(Locale.getDefault()));
                    }));

            // remove any of the old routes, so the old models will stop serving
            existingRoutes.forEach(Route::remove);

            LOGGER.info("Refreshed OData endpoint router, populated {} models, removed {} existing routes",
                    models.size(), existingRoutes.size());
            return succeededFuture();
        });
    }

    /**
     * Convenience method as similar other Vert.x handler implementations (e.g. ErrorHandler)
     *
     * @param serviceMetadata The metadata of the service
     * @return A ODataEndpointHandler instance
     */
    public static ODataEndpointHandler create(ServiceMetadata serviceMetadata) {
        return new ODataEndpointHandler(serviceMetadata);
    }

    private ODataEndpointHandler(ServiceMetadata serviceMetadata) {
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // In case the OData request is asynchronously processed, the processor will complete the processPromise
        // when done, in case Olingo handles the request synchronously, the processPromise will be completed here
        Vertx vertx = routingContext.vertx();
        Promise<Void> processPromise = Promise.promise();
        vertx.<ODataResponse>executeBlocking(blockingPromise -> {
            OData odata = OData.newInstance();
            ODataHandler odataHandler = odata.createRawHandler(serviceMetadata);

            // add further build-in processors for NeonBee here (every processor must handle the processPromise)
            odataHandler.register(new EntityCollectionProcessor(vertx, routingContext, processPromise));
            odataHandler.register(new EntityProcessor(vertx, routingContext, processPromise));
            odataHandler.register(new BatchProcessor(vertx, routingContext, processPromise));
            odataHandler.register(new PrimitiveProcessor(vertx, routingContext, processPromise));

            try {
                ODataResponse odataResponse = odataHandler.process(mapToODataRequest(routingContext,
                        serviceMetadata.getEdm().getEntityContainer().getNamespace()));
                // check for synchronous processing, complete the processPromise in case a response body is set
                if ((odataResponse.getStatusCode() != HTTP_INTERNAL_ERROR) || (odataResponse.getContent() != null)
                        || (odataResponse.getODataContent() != null)) {
                    processPromise.tryComplete();
                }
                blockingPromise.complete(odataResponse);
            } catch (ODataLibraryException e) {
                blockingPromise.fail(e);
            }
        }, asyncODataResponse -> {
            // failed to map / process OData request, so fail the web request
            if (asyncODataResponse.failed()) {
                routingContext.fail(asyncODataResponse.cause());
                return;
            }

            // the contents of the odataResponse could still be null, in case the processing was done
            // asynchronous, so wait for the processPromise to finish before continuing processing
            ODataResponse odataResponse = asyncODataResponse.result();
            processPromise.future().onComplete(asyncResult -> {
                // (asynchronously) retrieving the odata response failed, so fail the web request
                if (asyncResult.failed()) {
                    routingContext.fail(asyncResult.cause());
                    return;
                }

                try {
                    // map the odataResponse to the routingContext.response
                    mapODataResponse(odataResponse, routingContext.response());
                } catch (IOException | ODataRuntimeException e) {
                    routingContext.fail(e);
                }
            });
        });
    }

    /**
     * Maps a Vert.x RoutingContext into a new ODataRequest.
     *
     * @param routingContext  the context for the handling of the HTTP request
     * @param schemaNamespace the name of the service namespace
     * @return A new ODataRequest
     * @throws ODataLibraryException ODataLibraryException
     */
    @VisibleForTesting
    static ODataRequest mapToODataRequest(RoutingContext routingContext, String schemaNamespace)
            throws ODataLibraryException {
        HttpServerRequest request = routingContext.request();
        Buffer requestBody = routingContext.getBody();
        Route route = routingContext.currentRoute();
        String requestPath = request.path(); // routePath w/ exactly one tailing slash
        String requestQuery = request.query();
        // note that getPath returns *only* the path prefix, so essentially the uriPath with leading and tailing slashes
        // w/o the tailing *, the * is handled and stripped by the router!
        String routePath = Optional.ofNullable(route).map(Route::getPath).orElse(EMPTY).replaceAll("/+$", EMPTY) + "/";
        if (!requestPath.contains(routePath)) {
            // special case if calling the service root at /odata/svc, always append a forward slash for easier handling
            requestPath += "/";
        }

        // When loose / CDS URI path mapping is used, replace the last occurrence of the routePath, w/ the service
        // namespace, so the entity verticle can always assume that the full namespace will be part of the request
        // path. Find the last occurrence, as the service name could match the basePath of the router and thus e.g.
        // "/odata/odata" would get replaced with "/namespace/odata" instead the expected "/odata/namespace". As the
        // entity set name never contains a forward slash, replacing the last occurrence of the routePath (which
        // contains a tailing /) is always safe!
        String servicePath = "/" + schemaNamespace + "/";
        if (routePath.isEmpty()) {
            // replace last forward slash with service namespace, might not be accurate, but should work!
            requestPath = replaceLast(requestPath, "/", servicePath);
        } else {
            requestPath = replaceLast(requestPath, Pattern.quote(routePath), servicePath);
        }

        ODataRequest odataRequest = new ODataRequest();
        odataRequest.setBody(new BufferInputStream(requestBody));
        odataRequest.setProtocol(request.scheme());
        odataRequest.setMethod(mapODataRequestMethod(request));
        for (String header : request.headers().names()) {
            odataRequest.addHeader(header, request.headers().getAll(header));
        }

        /* @formatter:off
         * This is how the raw URI attributes need to be filled:
         *
         * <pre>
         *   rawRequestUri           = /my%20service/svc.sys1/Employees?$format=json,$top=10
         *   rawBaseUri              = /my%20service
         *   rawServiceResolutionUri = svc.sys1
         *   rawODataPath            = /Employees
         *   rawQueryPath            = $format=json,$top=10
         * </pre>
         *
         * This is what we have in the route handler:
         *
         * <pre>
         *   requestPath  = /my%20service/sys1/Employees
         *   requestQuery = $format=json,$top=10
         *   routePath    = /sys1/
         *   servicePath  = /svc.sys1/
         * </pre>
         */
        LOGGER.correlateWith(routingContext).debug(
                "OData request: requestPath={}, requestQuery={}, routePath={}, servicePath={}", requestPath,
                requestQuery, routePath, servicePath);
        // rawQueryPath contains the decoded query
        String rawQueryPath = requestQuery != null ? URLDecoder.decode(requestQuery, StandardCharsets.UTF_8) : null;
        String rawRequestUri = requestPath + (rawQueryPath == null ? "" : "?" + rawQueryPath);
        String rawBaseUri = requestPath.substring(0, requestPath.lastIndexOf(servicePath));
        String rawServiceResolutionUri = servicePath.replaceAll("^/+", EMPTY).replaceAll("/+$", EMPTY);
        String rawODataPath = requestPath.substring((rawBaseUri.length() + servicePath.length()) - 1);

        LOGGER.correlateWith(routingContext).debug(
                "OData request: rawRequestUri={}, rawBaseUri={}, rawServiceResolutionUri={}, rawODataPath={}, rawQueryPath={}",
                rawRequestUri, rawBaseUri, rawServiceResolutionUri, rawODataPath, rawQueryPath);

        odataRequest.setRawRequestUri(rawRequestUri);
        odataRequest.setRawBaseUri(rawBaseUri);
        odataRequest.setRawServiceResolutionUri(rawServiceResolutionUri);
        odataRequest.setRawODataPath(rawODataPath);
        odataRequest.setRawQueryPath(rawQueryPath);

        return odataRequest;
    }

    /**
     * Maps the Vert.x HttpServerRequest method to a valid OData HttpMethod, considering the request method, the
     * X-HTTP-Method and X-HTTP-Method-Override headers.
     *
     * @param request The incoming HttpRequest to map
     * @return A valid OData HttpMethod
     * @throws ODataLibraryException ODataLibraryException
     */
    @VisibleForTesting
    @SuppressWarnings("checkstyle:LocalVariableName")
    static HttpMethod mapODataRequestMethod(HttpServerRequest request) throws ODataLibraryException {
        HttpMethod odataRequestMethod;
        String rawMethod = request.method().name();
        try {
            odataRequestMethod = HttpMethod.valueOf(rawMethod);
        } catch (IllegalArgumentException e) {
            throw new ODataHandlerException("HTTP method not allowed" + rawMethod, e, HTTP_METHOD_NOT_ALLOWED,
                    rawMethod);
        }

        try { // in case it is a POST request, also consider the X-Http-Method and X-Http-Method-Override headers
            if (odataRequestMethod == HttpMethod.POST) {
                String xHttpMethod = request.getHeader(HttpHeader.X_HTTP_METHOD);
                String xHttpMethodOverride = request.getHeader(HttpHeader.X_HTTP_METHOD_OVERRIDE);

                if ((xHttpMethod == null) && (xHttpMethodOverride == null)) {
                    return odataRequestMethod;
                } else if (xHttpMethod == null) {
                    return HttpMethod.valueOf(xHttpMethodOverride);
                } else if (xHttpMethodOverride == null) {
                    return HttpMethod.valueOf(xHttpMethod);
                } else {
                    if (!xHttpMethod.equalsIgnoreCase(xHttpMethodOverride)) {
                        throw new ODataHandlerException("Ambiguous X-HTTP-Methods", AMBIGUOUS_XHTTP_METHOD, xHttpMethod,
                                xHttpMethodOverride);
                    }
                    return HttpMethod.valueOf(xHttpMethod);
                }
            } else {
                return odataRequestMethod;
            }
        } catch (IllegalArgumentException e) {
            throw new ODataHandlerException("Invalid HTTP method" + rawMethod, e, INVALID_HTTP_METHOD, rawMethod);
        }
    }

    /**
     * Maps a ODataResponse to a existing Vert.x HttpServerResponse.
     *
     * @param odataResponse The ODataResponse to map
     * @param response      The HttpServerResponse to map to
     * @throws IOException IOException
     */
    @VisibleForTesting
    static void mapODataResponse(ODataResponse odataResponse, HttpServerResponse response) throws IOException {
        // status code and headers
        response.setStatusCode(odataResponse.getStatusCode());
        for (Map.Entry<String, List<String>> entry : odataResponse.getAllHeaders().entrySet()) {
            for (String headerValue : entry.getValue()) {
                response.putHeader(entry.getKey(), headerValue);
            }
        }
        // OData response content
        if (odataResponse.getContent() != null) {
            response.end(inputStreamToBuffer(odataResponse.getContent()));
        } else if (odataResponse.getODataContent() != null) {
            ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
            odataResponse.getODataContent().write(byteArrayOutput);
            response.end(Buffer.buffer(byteArrayOutput.toByteArray()));
        } else {
            response.end(); // no content (e.g. for update / delete requests)
        }
    }
}
