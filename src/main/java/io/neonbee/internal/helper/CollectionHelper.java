package io.neonbee.internal.helper;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.Shareable;

public final class CollectionHelper {

    /**
     * The default load factor used in hash-based collections (e.g., HashMap, HashSet).
     * <p>
     * This matches the default load factor used internally by Java's standard library, and is used to calculate initial
     * capacity to minimize rehashing.
     */
    public static final float LOAD_FACTOR = 0.75f;

    /**
     * The default initial capacity for hash-based collections.
     * <p>
     * This value aligns with Java's default initial capacity for HashMap and HashSet when no explicit size is provided.
     */
    public static final int DEFAULT_CAPACITY = 16;

    /**
     * This helper class cannot be instantiated.
     */
    private CollectionHelper() {}

    /**
     * Create mutable deep copy of a list by creating a copy of all mutable items and adding them to a new list.
     *
     * @param <T>  the type of the list
     * @param list the list to copy
     * @return a mutable deep copy of the given list
     */
    public static <T> List<T> mutableCopyOf(List<T> list) {
        if (list == null) {
            return new ArrayList<>();
        }
        List<T> copy = new ArrayList<>(list.size());
        for (T item : list) {
            copy.add(copyOf(item));
        }
        return copy;
    }

    /**
     * Create mutable deep copy of a set by creating a copy of all mutable items and adding them to a new set.
     *
     * @param <T> the type of the set
     * @param set the set to copy
     * @return a mutable deep copy of the given set
     */
    public static <T> Set<T> mutableCopyOf(Set<T> set) {
        if (set == null) {
            return new HashSet<>();
        }

        int initialCapacity = Math.max((int) (set.size() / LOAD_FACTOR) + 1, DEFAULT_CAPACITY);

        Set<T> copy = new HashSet<>(initialCapacity);
        for (T item : set) {
            copy.add(copyOf(item));
        }
        return copy;
    }

    /**
     * Create mutable deep copy of a collection by creating a copy of all mutable items and adding them to another
     * collection, which is created by any given supplier.
     *
     * @param <T>               the type of the collection
     * @param <C>               the collection to return
     * @param collection        the collection to copy
     * @param collectionFactory the factory to create the collection to add the copied items into
     * @return a mutable deep copy of the given collection
     */
    public static <T, C extends Collection<T>> C mutableCopyOf(C collection, Supplier<C> collectionFactory) {
        C copy = collectionFactory.get();
        if (collection == null) {
            return copy;
        }
        for (T item : collection) {
            copy.add(copyOf(item));
        }
        return copy;
    }

    /**
     * Create mutable deep copy of a map by creating a copy of all mutable keys and values and adding them to a new map.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param map the map to copy
     * @return a mutable deep copy of the given map
     */
    public static <K, V> Map<K, V> mutableCopyOf(Map<K, V> map) {
        Map<K, V> copy = new NullLiberalMergingHashMap<>();
        if (map == null) {
            return copy;
        }
        for (Map.Entry<K, V> entry : map.entrySet()) {
            K keyCopy = copyOf(entry.getKey());
            V valueCopy = copyOf(entry.getValue());
            copy.put(keyCopy, valueCopy);
        }
        return copy;
    }

    /**
     * Creating a, if possible, mutable copy of a given object. If the object is immutable by definition or the object
     * cannot be copied, this method returns the same object. The following types are supported to copy:
     * <ul>
     * <li>{@code null}
     * <li>{@link Buffer}
     * <li>{@link List}
     * <li>{@link Set}
     * <li>{@link Map}
     * <li>any array type
     * <li>{@link Shareable}
     * </ul>
     *
     * @param <T>    the type of the object to copy
     * @param object the object to copy
     * @return either a new mutable copy of the object, or the object itself
     */
    @SuppressWarnings("unchecked")
    public static <T> T copyOf(T object) {
        if (Objects.isNull(object)) {
            return null;
        } else if (object instanceof Buffer buffer) {
            return (T) buffer.copy();
        } else if (object instanceof List<?> list) {
            return (T) mutableCopyOf(list);
        } else if (object instanceof Set<?> set) {
            return (T) mutableCopyOf(set);
        } else if (object instanceof Map<?, ?> map) {
            return (T) mutableCopyOf(map);
        } else if (object.getClass().isArray()) {
            if (object instanceof byte[] byteArr) {
                return (T) Arrays.copyOf(byteArr, byteArr.length);
            } else if (object instanceof short[] shortArr) {
                return (T) Arrays.copyOf(shortArr, shortArr.length);
            } else if (object instanceof int[] intArr) {
                return (T) Arrays.copyOf(intArr, intArr.length);
            } else if (object instanceof char[] charArr) {
                return (T) Arrays.copyOf(charArr, charArr.length);
            } else if (object instanceof float[] floatArr) {
                return (T) Arrays.copyOf(floatArr, floatArr.length);
            } else if (object instanceof double[] doubleArr) {
                return (T) Arrays.copyOf(doubleArr, doubleArr.length);
            } else if (object instanceof boolean[] booleanArr) {
                return (T) Arrays.copyOf(booleanArr, booleanArr.length);
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

    /**
     * Converts a given map {@link Map} to a case-insensitive treemap {@link TreeMap} by creating a copy of all mutable
     * keys and values and adding them to a new case-insensitive treemap {@link TreeMap}.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param map the map to copy
     * @return a new case-insensitive treemap as mutable deep copy of the given map
     */
    public static <K extends String, V> Map<K, V> mapToCaseInsensitiveTreeMap(Map<K, V> map) {
        Map<K, V> copy = new NullLiberalMergingTreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (map == null) {
            return copy;
        }
        for (Map.Entry<K, V> entry : map.entrySet()) {
            K keyCopy = copyOf(entry.getKey());
            V valueCopy = copyOf(entry.getValue());
            copy.put(keyCopy, valueCopy);
        }
        return copy;
    }

    /**
     * Converts a given {@link MultiMap} to a {@link Map} of a {@link List} of {@link String}.
     *
     * @param multiMap the {@link MultiMap} to convert
     * @return a flat list mapping the key to a list of values
     */
    public static Map<String, List<String>> multiMapToMap(MultiMap multiMap) {
        Map<String, List<String>> result = new HashMap<>();
        if (multiMap == null) {
            return result;
        }
        for (Map.Entry<String, String> entry : multiMap.entries()) {
            result.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
        }
        return result;
    }

    /**
     * Returns a collector converting Map.Entry&lt;K, U&gt; into a Map&lt;K, U&gt;.
     *
     * @param <K> the type of keys maintained by this map
     * @param <V> the type of mapped values
     * @return a collector converting entities into a map
     */
    public static <K, V> Collector<Map.Entry<K, V>, ?, Map<K, V>> identityMapCollector() {
        return Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    /**
     * Returns true, when a collection is null or empty.
     *
     * @param <T>        the type of entries
     * @param collection collection to check
     * @return true, when a collection is null or empty
     */
    public static <T> boolean isNullOrEmpty(Collection<T> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Returns true, when a map is null or empty.
     *
     * @param <K> the type of key
     * @param <V> the type of entry
     * @param map map to check
     * @return true, when a map is null or empty
     */
    public static <K, V> boolean isNullOrEmpty(Map<K, V> map) {
        return map == null || map.isEmpty();
    }

    /**
     * A merging {@link HashMap} which may take null values into merging.
     *
     * @param <K> the key type
     * @param <V> the value type
     */
    @SuppressWarnings("PMD.NullAssignment")
    public static class NullLiberalMergingHashMap<K, V> extends HashMap<K, V> {
        private static final long serialVersionUID = 1L;

        /**
         * Create a new {@link NullLiberalMergingHashMap}.
         */
        public NullLiberalMergingHashMap() {
            super();
        }

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

    /**
     * A merging {@link TreeMap} which may take null values into merging.
     *
     * @param <K> the key type
     * @param <V> the value type
     */
    @SuppressWarnings("PMD.NullAssignment")
    public static class NullLiberalMergingTreeMap<K, V> extends TreeMap<K, V> {
        private static final long serialVersionUID = 1L;

        /**
         * Constructor for NullLiberalMergingTreeMap with comparator.
         *
         * @param comparator the comparator for sorting
         */
        public NullLiberalMergingTreeMap(Comparator<? super K> comparator) {
            super(comparator);
        }

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
