package io.neonbee.internal;

import static io.vertx.core.Future.failedFuture;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import io.neonbee.NeonBee;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.Shareable;

@SuppressWarnings({ "PMD.ExcessivePublicCount", "checkstyle:MissingJavadocMethod", "checkstyle:JavadocVariable" })
public class Helper {

    public static final String EMPTY = "";

    public static final DeliveryOptions LOCAL_DELIVERY = new DeliveryOptions().setLocalOnly(true);

    @VisibleForTesting
    static final String CF_INSTANCE_INTERNAL_IP_ENV_KEY = "CF_INSTANCE_INTERNAL_IP";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final int DEFAULT_BUFFER_SIZE = 4096;

    private static final ObjectMapper YAML_MAPPER = new YAMLMapper();

    private static String currentIp;

    public static String replaceLast(String string, String regex, String replacement) {
        return string.replaceFirst("(?s)(.*)" + regex, "$1" + replacement);
    }

    @SuppressWarnings("unchecked")
    public static <T, U extends T> T[] concatenateArrays(T[] typeDefiningArrayA, U... arrayB) {
        return Stream.of(typeDefiningArrayA, arrayB).flatMap(Stream::of).toArray(length -> (T[]) new Object[length]);
    }

    public static <T> List<T> mutableCopyOf(List<T> list) {
        return Optional.ofNullable(list).map(List::stream).orElseGet(Stream::empty).map(Helper::copyOf)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static <T> Set<T> mutableCopyOf(Set<T> set) {
        return Optional.ofNullable(set).map(Set::stream).orElseGet(Stream::empty).map(Helper::copyOf)
                .collect(Collectors.toCollection(HashSet::new));
    }

    public static <T, C extends Collection<T>> C mutableCopyOf(C collection, Supplier<C> collectionFactory) {
        return Optional.ofNullable(collection).map(Collection::stream).orElseGet(Stream::empty).map(Helper::copyOf)
                .collect(Collectors.toCollection(collectionFactory));
    }

    public static <K, V> Map<K, V> mutableCopyOf(Map<K, V> map) {
        return Optional.ofNullable(map).map(Map::entrySet).map(Set::stream).orElseGet(Stream::empty)
                .collect(Collectors.toMap(entry -> copyOf(entry.getKey()), entry -> copyOf(entry.getValue()),
                        (valueA, valueB) -> valueB, NullLiberalMergingHashMap::new));
    }

    @SuppressWarnings("unchecked")
    public static <T> T copyOf(T object) {
        if (Objects.isNull(object)) {
            return null;
        } else if (object instanceof Buffer) {
            return (T) ((Buffer) object).copy();
        } else if (object instanceof List) {
            return (T) mutableCopyOf((List<?>) object);
        } else if (object instanceof Set) {
            return (T) mutableCopyOf((Set<?>) object);
        } else if (object instanceof Map) {
            return (T) mutableCopyOf((Map<?, ?>) object);
        } else if (object.getClass().isArray()) {
            if (object instanceof byte[]) {
                return (T) Arrays.copyOf((byte[]) object, ((byte[]) object).length);
            } else if (object instanceof short[]) {
                return (T) Arrays.copyOf((short[]) object, ((short[]) object).length);
            } else if (object instanceof int[]) {
                return (T) Arrays.copyOf((int[]) object, ((int[]) object).length);
            } else if (object instanceof char[]) {
                return (T) Arrays.copyOf((char[]) object, ((char[]) object).length);
            } else if (object instanceof float[]) {
                return (T) Arrays.copyOf((float[]) object, ((float[]) object).length);
            } else if (object instanceof double[]) {
                return (T) Arrays.copyOf((double[]) object, ((double[]) object).length);
            } else if (object instanceof boolean[]) {
                return (T) Arrays.copyOf((boolean[]) object, ((boolean[]) object).length);
            } else {
                Class<?> componentType = object.getClass().getComponentType();
                Object[] array = (Object[]) Array.newInstance(componentType, ((Object[]) object).length);
                Arrays.setAll(array, index -> copyOf(((Object[]) object)[index]));
                return (T) array;
            }
        } else if (object instanceof Shareable) {
            return (T) ((Shareable) object).copy();
        } else {
            return object;
        }
    }

    public static Map<String, List<String>> multiMapToMap(MultiMap multiMap) {
        return multiMap.entries().stream()
                .collect(Collectors.<Entry<String, String>, String, List<String>>toMap(Entry::getKey,
                        entry -> Collections.singletonList(entry.getValue()),
                        (listA, listB) -> Stream.concat(listA.stream(), listB.stream()).collect(Collectors.toList())));
    }

    /**
     * To be used like map.entrySet().stream().filter(entryPredicate((key, value) -&gt; ...)).
     *
     * @param predicate The predicate
     * @param <K>       The type of the key of the map entry
     * @param <V>       The type of the value of the map entry
     * @return A predicate which applies the logic of the passed predicate to the map
     */
    public static <K, V> Predicate<Map.Entry<K, V>> entryPredicate(BiPredicate<? super K, ? super V> predicate) {
        return entry -> predicate.test(entry.getKey(), entry.getValue());
    }

    /**
     * To be used like map.entrySet().stream().map(entryFunction((key, value) -&gt; ...)).
     *
     * @param function The {@link BiFunction} with the logic to be applied to the map entries
     * @param <K>      The type of the key of the map entry
     * @param <V>      The type of the value of the map entry
     * @param <R>      The type of the return value of the returned function
     * @return A function which applies the passed logic to the map
     */
    public static <K, V, R> Function<Map.Entry<K, V>, R> entryFunction(BiFunction<? super K, ? super V, R> function) {
        return entry -> function.apply(entry.getKey(), entry.getValue());
    }

    /**
     * To be used like map.entrySet().stream().forEach(entryConsumer((key, value) -&gt; ...)).
     *
     * @param consumer A {@link BiConsumer} of map key and value which contains the logic handling the map entry.
     * @param <K>      The type of the key of the returned map entry
     * @param <V>      The type of the value of the returned map entry
     * @return Returns a consumer
     */
    public static <K, V> Consumer<Map.Entry<K, V>> entryConsumer(BiConsumer<? super K, ? super V> consumer) {
        return entry -> consumer.accept(entry.getKey(), entry.getValue());
    }

    @SuppressWarnings({ "unchecked", "TypeParameterUnusedInFormals" })
    public static <T, U> T uncheckedMapper(U value) {
        return (T) value;
    }

    public static Buffer inputStreamToBuffer(InputStream input) throws IOException {
        byte[] data = new byte[DEFAULT_BUFFER_SIZE];

        int read;
        Buffer buffer = Buffer.buffer();
        while ((read = input.read(data, 0, data.length)) != -1) {
            buffer.appendBytes(data, 0, read);
        }

        return buffer;
    }

    @SuppressWarnings("PMD.UseProperClassLoader")
    public static ClassLoader getClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader == null ? Helper.class.getClassLoader() : classLoader;
    }

    public static Buffer readResourceToBuffer(String resource) {
        ClassLoader classLoader = getClassLoader();
        try {
            try (InputStream input = classLoader.getResourceAsStream(resource)) {
                if (input == null) {
                    return null;
                }

                return inputStreamToBuffer(input);
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Type safe CompositeFuture future (as soon as Vert.x applies this interface signature, switch to
     * CompositeFuture.all again).
     *
     * @param futures The futures to be checked for completeness
     * @return A {@link CompositeFuture}, succeeded if all of the passed futures succeeded, failing otherwise.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static CompositeFuture allComposite(List<? extends Future<?>> futures) {
        return CompositeFuture.all((List<Future>) (Object) futures);
    }

    /**
     * Type safe CompositeFuture future (as soon as Vert.x applies this interface signature, switch to
     * CompositeFuture.join again).
     *
     * @param futures The futures to be checked for completeness
     * @return A {@link CompositeFuture}, succeeded if any of the passed futures succeeded, failing otherwise.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static CompositeFuture anyComposite(List<? extends Future<?>> futures) {
        return CompositeFuture.any((List<Future>) (Object) futures);
    }

    /**
     * Type safe CompositeFuture future (as soon as Vert.x applies this interface signature, switch to
     * CompositeFuture.join again).
     *
     * @param futures A list of futures to be converted
     * @return A {@link CompositeFuture} containing the passed futures
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static CompositeFuture joinComposite(List<? extends Future<?>> futures) {
        return CompositeFuture.join((List<Future>) (Object) futures);
    }

    public static AsyncResult<?>[] asyncResults(CompositeFuture compositeFuture) {
        return typedAsyncResults(compositeFuture);
    }

    public static List<AsyncResult<?>> asyncResultList(CompositeFuture compositeFuture) {
        return Arrays.asList(asyncResults(compositeFuture));
    }

    public static <T> AsyncResult<T>[] typedAsyncResults(CompositeFuture compositeFuture) {
        @SuppressWarnings("unchecked")
        AsyncResult<T>[] asyncResults = new AsyncResult[compositeFuture.size()];
        Arrays.setAll(asyncResults, index -> new AsyncResult<T>() {
            @Override
            public T result() {
                return compositeFuture.resultAt(index);
            }

            @Override
            public Throwable cause() {
                return compositeFuture.cause(index);
            }

            @Override
            public boolean succeeded() {
                return compositeFuture.succeeded(index);
            }

            @Override
            public boolean failed() {
                return compositeFuture.failed(index);
            }
        });
        return asyncResults;
    }

    public static <T> List<AsyncResult<T>> typedAsyncResultList(CompositeFuture compositeFuture) {
        return Arrays.asList(typedAsyncResults(compositeFuture));
    }

    public static void parseYAML(Vertx vertx, Buffer buffer, Handler<AsyncResult<JsonObject>> resultHandler) {
        vertx.executeBlocking(blockingFuture -> {
            try {
                blockingFuture.complete(parseYAMLBlocking(buffer));
            } catch (Exception e) {
                blockingFuture.fail(e);
            }
        }, resultHandler);
    }

    public static JsonObject parseYAMLBlocking(Buffer buffer) {
        try {
            JsonNode node = YAML_MAPPER.readTree(buffer.getBytes());
            return new JsonObject(node.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void readYAML(Vertx vertx, String path, Handler<AsyncResult<JsonObject>> resultHandler) {
        Future.<Buffer>future(readHandler -> vertx.fileSystem().readFile(path, readHandler))
                .compose(buffer -> Future.<JsonObject>future(parseHandler -> parseYAML(vertx, buffer, parseHandler)))
                .onComplete(resultHandler);
    }

    public static JsonObject readYAMLBlocking(Vertx vertx, String path) {
        return parseYAMLBlocking(vertx.fileSystem().readFileBlocking(path));
    }

    public static void readJSON(Vertx vertx, String path, Handler<AsyncResult<JsonObject>> resultHandler) {
        Future.<Buffer>future(readHandler -> vertx.fileSystem().readFile(path, readHandler)).map(Buffer::toJsonObject)
                .onComplete(resultHandler);
    }

    public static JsonObject readJSONBlocking(Vertx vertx, String path) {
        return vertx.fileSystem().readFileBlocking(path).toJsonObject();
    }

    public static void readConfig(Vertx vertx, String identifier, Handler<AsyncResult<JsonObject>> resultHandler) {
        Path configDirPath = NeonBee.instance(vertx).getOptions().getConfigDirectory();
        Future.<JsonObject>future(readHandler -> readYAML(vertx,
                configDirPath.resolve(identifier + ".yaml").toAbsolutePath().toString(), readHandler))
                .recover(throwable -> throwable.getCause() instanceof NoSuchFileException
                        ? Future.future(readHandler -> readJSON(vertx,
                                configDirPath.resolve(identifier + ".json").toAbsolutePath().toString(), readHandler))
                        : failedFuture(throwable))
                .onComplete(resultHandler);
    }

    public static JsonObject readConfigBlocking(Vertx vertx, String identifier) {
        Path configDirPath = NeonBee.instance(vertx).getOptions().getConfigDirectory();
        try {
            return readYAMLBlocking(vertx, configDirPath.resolve(identifier + ".yaml").toAbsolutePath().toString());
        } catch (FileSystemException e) {
            if (e.getCause() instanceof NoSuchFileException) {
                return readJSONBlocking(vertx, configDirPath.resolve(identifier + ".json").toAbsolutePath().toString());
            } else {
                throw e;
            }
        }
    }

    public static JsonObject readConfigBlocking(Vertx vertx, String identifier, JsonObject fallback) {
        try {
            return readConfigBlocking(vertx, identifier);
        } catch (FileSystemException e) {
            return fallback;
        }
    }

    public static class BufferInputStream extends InputStream {
        private Buffer buffer;

        private int position;

        public BufferInputStream(Buffer buffer) {
            super();
            this.buffer = buffer;
        }

        public int getPosition() {
            return position;
        }

        @Override
        public int available() {
            return buffer.length() - position;
        }

        @Override
        @SuppressWarnings("checkstyle:magicnumber")
        public int read() {
            if (position == buffer.length()) {
                return -1;
            }

            // convert to unsigned byte
            return buffer.getByte(position++) & 0xFF;
        }

        @Override
        public int read(byte[] data, int offset, int length) {
            int size = Math.min(data.length, buffer.length() - position);
            if (size == 0) {
                return -1;
            }

            buffer.getBytes(position, position + size, data, offset);
            position += size;

            return size;
        }

        @SuppressWarnings("PMD.NullAssignment")
        @Override
        public void close() {
            buffer = null;
        }
    }

    public static boolean allSucceeded(AsyncResult<?>... asyncResults) {
        return Arrays.stream(asyncResults).allMatch(AsyncResult::succeeded);
    }

    public static boolean anySucceeded(AsyncResult<?>... asyncResults) {
        return Arrays.stream(asyncResults).anyMatch(AsyncResult::succeeded);
    }

    public static boolean allFailed(AsyncResult<?>... asyncResults) {
        return Arrays.stream(asyncResults).allMatch(AsyncResult::failed);
    }

    public static boolean anyFailed(AsyncResult<?>... asyncResults) {
        return Arrays.stream(asyncResults).anyMatch(AsyncResult::failed);
    }

    @SuppressWarnings("PMD.NullAssignment")
    public static class NullLiberalMergingHashMap<K, V> extends HashMap<K, V> {
        private static final long serialVersionUID = 1L;

        @Override
        public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
            if (value != null) {
                return super.merge(key, value, remappingFunction);
            } else {
                put(key, containsKey(key) ? remappingFunction.apply(get(key), null) : null);
                return null;
            }
        }
    }

    @SuppressWarnings("PMD.UseLocaleWithCaseConversions")
    public static boolean isMac() {
        try {
            return System.getProperty("os.name").toLowerCase().contains("mac");
        } catch (Exception e) {
            LOGGER.error("Error reading system property 'os.name' to determine the operating system", e);
            return false;
        }
    }

    @SuppressWarnings({ "PMD.AvoidUsingHardCodedIP", "PMD.NonThreadSafeSingleton" })
    public static String hostIp() {
        if (currentIp == null) {
            String ip = System.getenv(CF_INSTANCE_INTERNAL_IP_ENV_KEY);
            if (Strings.isNullOrEmpty(ip)) {
                try {
                    ip = InetAddress.getLocalHost().getHostAddress();
                } catch (UnknownHostException e) {
                    ip = "127.0.0.1";
                }
            }
            currentIp = ip;
        }
        return currentIp;
    }
}
