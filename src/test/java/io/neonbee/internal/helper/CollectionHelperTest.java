package io.neonbee.internal.helper;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.Shareable;

class CollectionHelperTest {

    @Test
    @DisplayName("Copy map with mutableCopyOf")
    @SuppressWarnings("PMD.LooseCoupling")
    void testMutableCopyOfMap() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("k", null);
        map.put("key", "value");
        map.put("k1", 1);
        Map<String, Object> resultMap = CollectionHelper.mutableCopyOf(map);
        assertThat(resultMap).isInstanceOf(HashMap.class);
        assertThat(resultMap).containsExactlyEntriesIn(map);
    }

    @Test
    @DisplayName("mutableCopyOf(Map) returns empty map for null")
    void testMutableCopyOfMapNull() {
        Map<String, Object> resultMap = CollectionHelper.mutableCopyOf((Map<String, Object>) null);
        assertThat(resultMap).isNotNull();
        assertThat(resultMap).isEmpty();
    }

    @Test
    @DisplayName("Copy list with mutableCopyOf")
    void testMutableCopyOfList() {
        List<String> list = List.of("a", "b", "c");
        List<String> result = CollectionHelper.mutableCopyOf(list);
        assertThat(result).containsExactlyElementsIn(list).inOrder();
        result.add("d");
        assertThat(result).hasSize(4);
    }

    @Test
    @DisplayName("mutableCopyOf(List) returns empty list for null")
    void testMutableCopyOfListNull() {
        List<String> result = CollectionHelper.mutableCopyOf((List<String>) null);
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Copy set with mutableCopyOf")
    void testMutableCopyOfSet() {
        Set<String> set = Set.of("x", "y", "z");
        Set<String> result = CollectionHelper.mutableCopyOf(set);
        assertThat(result).containsExactlyElementsIn(set);
        result.add("w");
        assertThat(result).hasSize(4);
    }

    @Test
    @DisplayName("mutableCopyOf(Set) returns empty set for null")
    void testMutableCopyOfSetNull() {
        Set<String> result = CollectionHelper.mutableCopyOf((Set<String>) null);
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Copy collection with mutableCopyOf and supplier")
    @SuppressWarnings("PMD.LooseCoupling")
    void testMutableCopyOfCollectionWithSupplier() {
        ArrayList<String> collection = new ArrayList<>(List.of("1", "2", "3"));
        ArrayList<String> result = CollectionHelper.mutableCopyOf(collection, ArrayList::new);
        assertThat(result).containsExactlyElementsIn(collection).inOrder();
        assertThat(result).isInstanceOf(ArrayList.class);
    }

    @Test
    @DisplayName("mutableCopyOf(Collection, Supplier) returns empty collection for null")
    void testMutableCopyOfCollectionWithSupplierNull() {
        ArrayList<String> result = CollectionHelper.mutableCopyOf(null, ArrayList::new);
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Copy map with mapToCaseInsensitiveTreeMap")
    @SuppressWarnings("PMD.LooseCoupling")
    void testMapToCaseInsensitiveTreeMap() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("k", null);
        map.put("key", "value");
        map.put("k1", 1);
        Map<String, Object> resultMap = CollectionHelper.mapToCaseInsensitiveTreeMap(map);
        assertThat(resultMap).isInstanceOf(TreeMap.class);
        assertThat(resultMap).containsExactlyEntriesIn(map);
    }

    @Test
    @DisplayName("mapToCaseInsensitiveTreeMap returns empty map for null")
    void testMapToCaseInsensitiveTreeMapNull() {
        Map<String, Object> resultMap = CollectionHelper.mapToCaseInsensitiveTreeMap(null);
        assertThat(resultMap).isNotNull();
        assertThat(resultMap).isEmpty();
    }

    @Test
    @DisplayName("multiMapToMap converts MultiMap correctly")
    void testMultiMapToMap() {
        MultiMap multiMap = MultiMap.caseInsensitiveMultiMap();
        multiMap.add("color", "red");
        multiMap.add("color", "blue");
        multiMap.add("size", "large");

        Map<String, List<String>> result = CollectionHelper.multiMapToMap(multiMap);
        assertThat(result).containsKey("color");
        assertThat(result.get("color")).containsExactly("red", "blue");
        assertThat(result).containsKey("size");
        assertThat(result.get("size")).containsExactly("large");
    }

    @Test
    @DisplayName("multiMapToMap returns empty map for null")
    void testMultiMapToMapNull() {
        Map<String, List<String>> result = CollectionHelper.multiMapToMap(null);
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("copyOf returns null for null input")
    void testCopyOfNull() {
        Object result = CollectionHelper.copyOf(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("copyOf copies Buffer")
    void testCopyOfBuffer() {
        Buffer original = Buffer.buffer("hello");
        Buffer copy = CollectionHelper.copyOf(original);
        assertThat(copy).isNotSameInstanceAs(original);
        assertThat(copy.toString()).isEqualTo("hello");
    }

    @Test
    @DisplayName("copyOf deep copies List")
    void testCopyOfList() {
        List<String> original = new ArrayList<>(List.of("a", "b"));
        List<String> copy = CollectionHelper.copyOf(original);
        assertThat(copy).isNotSameInstanceAs(original);
        assertThat(copy).containsExactly("a", "b");
    }

    @Test
    @DisplayName("copyOf deep copies Set")
    void testCopyOfSet() {
        Set<String> original = new HashSet<>(Set.of("x", "y"));
        Set<String> copy = CollectionHelper.copyOf(original);
        assertThat(copy).isNotSameInstanceAs(original);
        assertThat(copy).containsExactly("x", "y");
    }

    @Test
    @DisplayName("copyOf deep copies Map")
    void testCopyOfMap() {
        Map<String, String> original = new HashMap<>(Map.of("k", "v"));
        Map<String, String> copy = CollectionHelper.copyOf(original);
        assertThat(copy).isNotSameInstanceAs(original);
        assertThat(copy).containsExactly("k", "v");
    }

    @Test
    @DisplayName("copyOf copies byte array")
    void testCopyOfByteArray() {
        byte[] original = { 1, 2, 3 };
        byte[] copy = CollectionHelper.copyOf(original);
        assertThat(copy).isNotSameInstanceAs(original);
        assertThat(copy).isEqualTo(new byte[] { 1, 2, 3 });
    }

    @Test
    @DisplayName("copyOf copies Object array deeply")
    void testCopyOfObjectArray() {
        String[] original = { "a", "b" };
        String[] copy = CollectionHelper.copyOf(original);
        assertThat(copy).isNotSameInstanceAs(original);
        assertThat(copy).asList().containsExactly("a", "b");
    }

    @Test
    @DisplayName("copyOf copies Shareable")
    void testCopyOfShareable() {
        TestShareable original = new TestShareable("data");
        TestShareable copy = CollectionHelper.copyOf(original);
        assertThat(copy).isNotSameInstanceAs(original);
        assertThat(copy.value).isEqualTo("data");
    }

    @Test
    @DisplayName("copyOf returns same instance for immutable types")
    void testCopyOfImmutable() {
        String str = "immutable";
        assertThat(CollectionHelper.copyOf(str)).isSameInstanceAs(str);
        Integer num = 42;
        assertThat(CollectionHelper.copyOf(num)).isSameInstanceAs(num);
    }

    @Test
    @DisplayName("isNullOrEmpty for collections")
    void testIsNullOrEmptyCollection() {
        assertThat(CollectionHelper.isNullOrEmpty((List<?>) null)).isTrue();
        assertThat(CollectionHelper.isNullOrEmpty(List.of())).isTrue();
        assertThat(CollectionHelper.isNullOrEmpty(List.of("a"))).isFalse();
    }

    @Test
    @DisplayName("isNullOrEmpty for maps")
    void testIsNullOrEmptyMap() {
        assertThat(CollectionHelper.isNullOrEmpty((Map<?, ?>) null)).isTrue();
        assertThat(CollectionHelper.isNullOrEmpty(Map.of())).isTrue();
        assertThat(CollectionHelper.isNullOrEmpty(Map.of("k", "v"))).isFalse();
    }

    @Test
    @DisplayName("copyOf handles self-referencing map without StackOverflowError")
    void testCopyOfSelfReferencingMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");
        map.put("self", map);

        Map<String, Object> copy = CollectionHelper.copyOf(map);

        assertThat(copy).isNotSameInstanceAs(map);
        assertThat(copy.get("key")).isEqualTo("value");
        assertThat(copy.get("self")).isSameInstanceAs(copy);
    }

    @Test
    @DisplayName("copyOf handles self-referencing list without StackOverflowError")
    @SuppressWarnings("unchecked")
    void testCopyOfSelfReferencingList() {
        List<Object> list = new ArrayList<>();
        list.add("item");
        list.add(list);

        List<Object> copy = CollectionHelper.copyOf(list);

        assertThat(copy).isNotSameInstanceAs(list);
        assertThat(copy.get(0)).isEqualTo("item");
        assertThat(copy.get(1)).isSameInstanceAs(copy);
    }

    @Test
    @DisplayName("copyOf handles set with circular reference without StackOverflowError")
    @SuppressWarnings("unchecked")
    void testCopyOfSetWithCircularReference() {
        Map<String, Object> map = new HashMap<>();
        Set<Object> set = new HashSet<>();
        set.add("item");
        set.add(map);
        map.put("set", set);

        Set<Object> copy = CollectionHelper.copyOf(set);

        assertThat(copy).isNotSameInstanceAs(set);
        assertThat(copy).contains("item");
        Map<String, Object> innerMap = (Map<String, Object>) copy.stream()
                .filter(e -> e instanceof Map).findFirst().orElseThrow();
        assertThat(innerMap.get("set")).isSameInstanceAs(copy);
    }

    @Test
    @DisplayName("copyOf handles mutually referencing maps without StackOverflowError")
    @SuppressWarnings("unchecked")
    void testCopyOfMutuallyReferencingMaps() {
        Map<String, Object> mapA = new HashMap<>();
        Map<String, Object> mapB = new HashMap<>();
        mapA.put("ref", mapB);
        mapB.put("ref", mapA);

        Map<String, Object> copyA = CollectionHelper.copyOf(mapA);
        Map<String, Object> copyB = (Map<String, Object>) copyA.get("ref");

        assertThat(copyA).isNotSameInstanceAs(mapA);
        assertThat(copyB).isNotSameInstanceAs(mapB);
        assertThat(copyB.get("ref")).isSameInstanceAs(copyA);
    }

    private static class TestShareable implements Shareable {
        final String value;

        TestShareable(String value) {
            this.value = value;
        }

        @Override
        public Shareable copy() {
            return new TestShareable(value);
        }
    }
}
