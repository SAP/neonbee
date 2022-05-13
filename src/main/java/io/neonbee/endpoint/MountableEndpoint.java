package io.neonbee.endpoint;

import static io.vertx.core.Future.failedFuture;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Strings;

import io.neonbee.config.AuthHandlerConfig;
import io.neonbee.config.EndpointConfig;
import io.neonbee.config.ServerConfig;
import io.neonbee.internal.handler.AuthChainHandler;
import io.neonbee.internal.handler.HooksHandler;
import io.neonbee.internal.helper.AsyncHelper;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;

public final class MountableEndpoint {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private final EndpointConfig endpointConfig;

    private final Endpoint endpoint;

    private final Router endpointRouter;

    /**
     * Creates a MountableEndpoint instance.
     *
     * @param vertx          the related Vert.x instance
     * @param endpointConfig the configuration to create the endpoint
     * @return a {@link Future}, succeeded if the endpoint was loaded successfully, failing otherwise.
     */
    public static Future<MountableEndpoint> create(Vertx vertx, EndpointConfig endpointConfig) {
        String endpointType = endpointConfig.getType();
        if (Strings.isNullOrEmpty(endpointType)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Endpoint with configuration {} is missing the 'type' field",
                        endpointConfig.toJson().encode());
            }
            return failedFuture(new IllegalArgumentException("Endpoint is missing the 'type' field"));
        }

        return AsyncHelper.executeBlocking(vertx, () -> loadClass(endpointType)).compose(endpoint -> {
            JsonObject endpointAdditionalConfig = Optional.ofNullable(endpoint.getDefaultConfig().getAdditionalConfig())
                    .map(JsonObject::copy).orElseGet(JsonObject::new);
            Optional.ofNullable(endpointConfig.getAdditionalConfig()).ifPresent(endpointAdditionalConfig::mergeIn);

            try {
                String endpointBasePath = getEndpointBasePath(endpointConfig, endpoint);
                return endpoint.createEndpointRouter(vertx, endpointBasePath, endpointAdditionalConfig)
                        .map(endpointRouter -> new MountableEndpoint(endpointConfig, endpoint, endpointRouter));
            } catch (Exception e) {
                LOGGER.error("Failed to initialize endpoint router for endpoint with type {} with configuration {}",
                        endpointType, endpointAdditionalConfig, e);
                return failedFuture(e);
            }
        });
    }

    private MountableEndpoint(EndpointConfig endpointConfig, Endpoint endpoint, Router endpointRouter) {
        this.endpointConfig = endpointConfig;
        this.endpoint = endpoint;
        this.endpointRouter = endpointRouter;
    }

    /**
     * Mounts the related {@link Endpoint} on the passed router. The authentication handlers are generated and deployed
     * based on the effective AuthHandlerConfig. The order to determine the effective configuration is as follows:
     * <ol>
     * <li>Check if an endpoint-specific auth. chain configuration exists.</li>
     * <li>Check if a default auth. chain configuration for the endpoint exists.</li>
     * <li>Check if a global auth. chain configuration in the {@link ServerConfig} exists.</li>
     * </ol>
     * There are two scenarios where no authentication checks are added for an endpoint:
     * <ul>
     * <li>An auth. chain configuration was found but it was empty.</li>
     * <li>No auth. chain configuration was found at all.</li>
     * </ul>
     *
     * @param vertx              the Vert.x instance to create the configured auth handlers
     * @param rootRouter         the router on which the endpoint will be mounted
     * @param defaultAuthHandler the default auth handler if there is no endpoint-specific one
     */
    public void mount(Vertx vertx, Router rootRouter, Optional<AuthChainHandler> defaultAuthHandler) {
        String endpointBasePath = getEndpointBasePath(endpointConfig, endpoint);
        Route endpointRoute = rootRouter.route(endpointBasePath + "*");

        Optional<List<AuthHandlerConfig>> effectiveAuthChainConfig =
                Optional.ofNullable(endpointConfig.getAuthChainConfig())
                        .or(() -> Optional.ofNullable(endpoint.getDefaultConfig().getAuthChainConfig()));
        effectiveAuthChainConfig.map(authChainConfig -> AuthChainHandler.create(vertx, authChainConfig))
                .or(() -> defaultAuthHandler).ifPresent(handler -> endpointRoute.handler(handler));

        endpointRoute.handler(new HooksHandler());

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(
                    "Mounting endpoint with type {} and configuration {}"
                            + "to base path {} using {} authentication handler",
                    endpointConfig.getType(), endpointConfig, endpointBasePath,
                    effectiveAuthChainConfig.map(authenticationHandler -> "an").orElse("no"));
        }

        // requires a new route object, thus do not use the endpointRoute here, but call mountSubRouter instead
        endpointRoute.subRouter(endpointRouter);
    }

    private static String getEndpointBasePath(EndpointConfig endpointConfig, Endpoint endpoint) {
        String endpointBasePath =
                Optional.ofNullable(endpointConfig.getBasePath()).orElse(endpoint.getDefaultConfig().getBasePath());
        if (!endpointBasePath.endsWith("/")) {
            endpointBasePath += "/";
        }

        return endpointBasePath;
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private static Endpoint loadClass(String endpointType) throws Exception {
        try {
            return Class.forName(endpointType).asSubclass(Endpoint.class).getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            LOGGER.error("No class for endpoint type {}", endpointType, e);
            throw new IllegalArgumentException("Endpoint class not found", e);
        } catch (ClassCastException e) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Endpoint type {} must implement {}", endpointType, Endpoint.class.getName(), e);
            }
            throw new IllegalArgumentException("Endpoint does not implement the Endpoint interface", e);
        } catch (NoSuchMethodException e) {
            LOGGER.error("Endpoint type {} must expose an empty constructor", endpointType, e);
            throw new IllegalArgumentException("Endpoint does not expose an empty constructor", e);
        } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
            LOGGER.error("Endpoint type {} could not be instantiated or threw an exception", endpointType, e);
            throw Optional.ofNullable((Exception) e.getCause()).orElse(e);
        }
    }
}
