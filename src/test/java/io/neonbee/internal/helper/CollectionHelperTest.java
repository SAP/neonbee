package io.neonbee.internal.helper;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CollectionHelperTest {

    @Test
    @DisplayName("Copy map with mutableCopyOf")
    @SuppressWarnings("PMD.LooseCoupling")
    void testMutableCopyOf() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("k", null);
        map.put("key", "value");
        map.put("k1", 1);
        Map<String, Object> resultMap = CollectionHelper.mutableCopyOf(map);
        assertThat(resultMap).isInstanceOf(HashMap.class);
        assertThat(resultMap).containsExactlyEntriesIn(map);

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
}
