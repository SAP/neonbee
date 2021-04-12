package io.neonbee.internal.helper;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.Shareable;

public final class CollectionsHelper {
    /**
     * This helper class cannot be instantiated
     */
    private CollectionsHelper() {}

    @SuppressWarnings("unchecked")
    public static <T, U extends T> T[] concatenateArrays(T[] typeDefiningArrayA, U... arrayB) {
        return Stream.of(typeDefiningArrayA, arrayB).flatMap(Stream::of).toArray(length -> (T[]) new Object[length]);
    }

    public static <T> List<T> mutableCopyOf(List<T> list) {
        return Optional.ofNullable(list).map(List::stream).orElseGet(Stream::empty).map(CollectionsHelper::copyOf)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static <T> Set<T> mutableCopyOf(Set<T> set) {
        return Optional.ofNullable(set).map(Set::stream).orElseGet(Stream::empty).map(CollectionsHelper::copyOf)
                .collect(Collectors.toCollection(HashSet::new));
    }

    public static <T, C extends Collection<T>> C mutableCopyOf(C collection, Supplier<C> collectionFactory) {
        return Optional.ofNullable(collection).map(Collection::stream).orElseGet(Stream::empty)
                .map(CollectionsHelper::copyOf).collect(Collectors.toCollection(collectionFactory));
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
                .collect(Collectors.<Map.Entry<String, String>, String, List<String>>toMap(Map.Entry::getKey,
                        entry -> Collections.singletonList(entry.getValue()),
                        (listA, listB) -> Stream.concat(listA.stream(), listB.stream()).collect(Collectors.toList())));
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

}
