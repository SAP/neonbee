package io.neonbee.hook.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.neonbee.NeonBee;
import io.neonbee.hook.Hook;
import io.neonbee.hook.HookContext;
import io.neonbee.hook.HookRegistration;
import io.neonbee.hook.HookRegistry;
import io.neonbee.hook.HookType;
import io.neonbee.hook.Hooks;
import io.neonbee.internal.Helper;
import io.neonbee.internal.helper.AsyncHelper;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * The default hook registry implementation, backed by synchronized in-memory maps.
 */
public class DefaultHookRegistry implements HookRegistry {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final int NUMBER_HOOK_PARAMETERS = 3;

    final Map<HookType, List<HookRegistration>> hookRegistry;

    private final Vertx vertx;

    /**
     * Creates a new DefaultHookRegistry.
     *
     * @param vertx a Vert.x instance to execute blocking code
     */
    public DefaultHookRegistry(Vertx vertx) {
        this.vertx = vertx;
        this.hookRegistry = new ConcurrentHashMap<>();
    }

    @Override
    public Future<Collection<HookRegistration>> registerInstanceHooks(Object instance, String correlationId) {
        return AsyncHelper.executeBlocking(vertx, () -> findHooks(instance, correlationId)).map(hookRegistrations -> {
            hookRegistrations.forEach(registration -> {
                LOGGER.correlateWith(correlationId).info("Registering hook {}", registration.getName());
                hookRegistry.computeIfAbsent(registration.getType(),
                        type -> Collections.synchronizedList(new ArrayList<>())).add(registration);
            });

            return hookRegistrations;
        });
    }

    @Override
    public CompositeFuture executeHooks(HookType type, Map<String, Object> parameters) {
        List<Future<Void>> hookExecutions = hookRegistry.getOrDefault(type, List.of()).stream()
                .map(DefaultHookRegistration.class::cast).map(registration -> executeHook(NeonBee.instance(vertx),
                        registration, DefaultHookContext.of(type, parameters)))
                .collect(Collectors.toList());

        return Helper.allComposite(hookExecutions);
    }

    @Override
    public Future<Collection<HookRegistration>> getHookRegistrations() {
        Collection<HookRegistration> registrations = hookRegistry.entrySet().stream().map(Map.Entry::getValue)
                .flatMap(List::stream).collect(Collectors.toList());
        return Future.succeededFuture(registrations);
    }

    private Future<Void> executeHook(NeonBee neonbee, DefaultHookRegistration hookRegistration, HookContext context) {
        return Future.future(promise -> {
            try {
                hookRegistration.getHookMethod().invoke(hookRegistration.getRelatedObject(), neonbee, context, promise);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                promise.fail(e);
            }
        });
    }

    private List<HookRegistration> findHooks(Object hookObject, String correlationId) {
        return Arrays.stream(hookObject.getClass().getMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()) && isHook(method)).filter(method -> {
                    if (matchesHookSignature(method)) {
                        return true;
                    } else {
                        LOGGER.correlateWith(correlationId).error(
                                "The hook method {} of class {} does not comply to the required method signature ([{}, {}, {}])",
                                method.getName(), hookObject.getClass().getName(), NeonBee.class.getName(),
                                HookContext.class.getName(), Promise.class.getName());
                        return false;
                    }
                }).map(method -> buildHookRegistrations(method, hookObject)).flatMap(s -> s)
                .collect(Collectors.toUnmodifiableList());
    }

    private Stream<HookRegistration> buildHookRegistrations(Method method, Object hookObject) {
        return Arrays.stream(method.getAnnotationsByType(Hook.class))
                .map(annotation -> new DefaultHookRegistration(this, hookObject, method, annotation.value()));
    }

    private static boolean matchesHookSignature(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        return parameterTypes.length == NUMBER_HOOK_PARAMETERS && NeonBee.class.isAssignableFrom(parameterTypes[0])
                && HookContext.class.isAssignableFrom(parameterTypes[1])
                && Promise.class.isAssignableFrom(parameterTypes[2]);
    }

    private static boolean isHook(Method method) {
        return method.isAnnotationPresent(Hook.class) || method.isAnnotationPresent(Hooks.class);
    }
}
