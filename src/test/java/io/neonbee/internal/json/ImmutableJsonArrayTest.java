package io.neonbee.internal.json;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ImmutableJsonArrayTest {
    @Test
    void testImmutableClass() {
        assertThat(new ImmutableJsonArray().getList().getClass()).isEqualTo(unmodifiableList(emptyList()).getClass());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testImmutable() {
        ImmutableJsonArray jsonArray = new ImmutableJsonArray();

        assertThrows(UnsupportedOperationException.class, () -> jsonArray.add(true));
        assertThrows(UnsupportedOperationException.class, () -> jsonArray.add(1));
        assertThrows(UnsupportedOperationException.class, () -> jsonArray.add(1L));
        assertThrows(UnsupportedOperationException.class, () -> jsonArray.add(1.f));
        assertThrows(UnsupportedOperationException.class, () -> jsonArray.add(1.));
        assertThrows(UnsupportedOperationException.class, () -> jsonArray.add("String"));
        assertThrows(UnsupportedOperationException.class, () -> jsonArray.add(new JsonObject()));
        assertThrows(UnsupportedOperationException.class, () -> jsonArray.add(new JsonArray()));
        assertThrows(UnsupportedOperationException.class, () -> jsonArray.add(new byte[] { 0, 1 }));
        assertThrows(UnsupportedOperationException.class, () -> jsonArray.addNull());

        assertThrows(UnsupportedOperationException.class, () -> jsonArray.clear());
        assertThrows(UnsupportedOperationException.class, () -> jsonArray.getList().add(true));
        assertThrows(UnsupportedOperationException.class, () -> jsonArray.addAll(new JsonArray().add(true)));
        assertThrows(UnsupportedOperationException.class, () -> jsonArray.iterator().remove());
        assertThrows(UnsupportedOperationException.class, () -> jsonArray.remove(0));
    }

    @Test
    void testImmutableConstruction() {
        assertThrows(UnsupportedOperationException.class, () -> new ImmutableJsonArray().add(true));
        assertThrows(UnsupportedOperationException.class, () -> new ImmutableJsonArray("[]").add(true));
        assertThrows(UnsupportedOperationException.class, () -> new ImmutableJsonArray(List.of(1, 2, 3)).add(true));
        assertThrows(UnsupportedOperationException.class, () -> new ImmutableJsonArray(Buffer.buffer("[]")).add(true));
        assertThrows(UnsupportedOperationException.class, () -> new ImmutableJsonArray(new JsonArray()).add(true));
    }

    @Test
    void testGetPrimitives() {
        ImmutableJsonArray jsonArray = new ImmutableJsonArray(new JsonArray().add(1).add(true).add("String").addNull());
        assertThat(jsonArray.getInteger(0)).isEqualTo(1);
        assertThat(jsonArray.getBoolean(1)).isEqualTo(true);
        assertThat(jsonArray.getString(2)).isEqualTo("String");
        assertThat(jsonArray.getValue(3)).isNull();
        assertThat(jsonArray.contains("String")).isEqualTo(true);
        assertThat(jsonArray.contains(false)).isEqualTo(false);
        assertThat(jsonArray.encode()).isEqualTo("[1,true,\"String\",null]");
        assertThat(jsonArray.isEmpty()).isEqualTo(false);
        assertThat(jsonArray.size()).isEqualTo(4);
    }

    @Test
    void testGetComplexValues() {
        ImmutableJsonArray jsonArray = new ImmutableJsonArray(new JsonArray()
                .add(new JsonObject().put("key1", 1).put("key2", true).put("key3", "String").putNull("key4"))
                .add(new JsonArray().add(1).add(2).add(3)));

        assertThat(jsonArray.getJsonObject(0).getMap()).containsExactly("key1", 1, "key2", true, "key3", "String",
                "key4", null);
        // also check that the returned complex type is immutable
        assertThrows(UnsupportedOperationException.class, () -> jsonArray.getJsonObject(0).put("keyX", true));
        assertThrows(UnsupportedOperationException.class, () -> ((JsonObject) jsonArray.getValue(0)).put("keyX", true));

        assertThat(jsonArray.getJsonArray(1).getList()).containsExactly(1, 2, 3);
        // also check that the returned complex type is immutable
        assertThrows(UnsupportedOperationException.class, () -> jsonArray.getJsonArray(1).add(true));
        assertThrows(UnsupportedOperationException.class, () -> ((JsonArray) jsonArray.getValue(1)).add(true));
    }

    @Test
    void testMutableCopyIsMutable() {
        assertDoesNotThrow(() -> new ImmutableJsonArray().mutableCopy().add(true));
    }

    @Test
    void testCopyIsAlsoNotMutable() {
        assertThrows(UnsupportedOperationException.class, () -> new ImmutableJsonArray().copy().add(true));
    }
}
