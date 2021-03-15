package io.neonbee.internal.json;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ImmutableJsonObjectTest {
    @Test
    void testImmutableClass() {
        assertThat(new ImmutableJsonObject().getMap().getClass()).isEqualTo(unmodifiableMap(emptyMap()).getClass());
    }

    @Test
    void testImmutable() {
        ImmutableJsonObject jsonObject = new ImmutableJsonObject();

        assertThrows(UnsupportedOperationException.class, () -> jsonObject.put("keyX", true));
        assertThrows(UnsupportedOperationException.class, () -> jsonObject.put("keyX", 1));
        assertThrows(UnsupportedOperationException.class, () -> jsonObject.put("keyX", 1L));
        assertThrows(UnsupportedOperationException.class, () -> jsonObject.put("keyX", 1.f));
        assertThrows(UnsupportedOperationException.class, () -> jsonObject.put("keyX", 1.));
        assertThrows(UnsupportedOperationException.class, () -> jsonObject.put("keyX", "String"));
        assertThrows(UnsupportedOperationException.class, () -> jsonObject.put("keyX", new JsonObject()));
        assertThrows(UnsupportedOperationException.class, () -> jsonObject.put("keyX", new JsonArray()));
        assertThrows(UnsupportedOperationException.class, () -> jsonObject.put("keyX", new byte[] { 0, 1 }));
        assertThrows(UnsupportedOperationException.class, () -> jsonObject.putNull("keyX"));

        assertThrows(UnsupportedOperationException.class, () -> jsonObject.clear());
        assertThrows(UnsupportedOperationException.class, () -> jsonObject.getMap().put("keyX", true));
        assertThrows(UnsupportedOperationException.class, () -> jsonObject.mergeIn(new JsonObject().put("keyX", true)));
        assertThrows(UnsupportedOperationException.class, () -> jsonObject.iterator().remove());
        assertThrows(UnsupportedOperationException.class, () -> jsonObject.remove("keyX"));
    }

    @Test
    void testImmutableConstruction() {
        assertThrows(UnsupportedOperationException.class, () -> new ImmutableJsonObject().put("keyX", true));
        assertThrows(UnsupportedOperationException.class, () -> new ImmutableJsonObject("{}").put("keyX", true));
        assertThrows(UnsupportedOperationException.class,
                () -> new ImmutableJsonObject(Map.of("keyX", true)).put("keyY", true));
        assertThrows(UnsupportedOperationException.class,
                () -> new ImmutableJsonObject(Buffer.buffer("{}")).put("keyX", true));
        assertThrows(UnsupportedOperationException.class,
                () -> new ImmutableJsonObject(new JsonObject()).put("keyX", true));
    }

    @Test
    void testGetPrimitives() {
        ImmutableJsonObject jsonObject = new ImmutableJsonObject(
                new JsonObject().put("key1", 1).put("key2", true).put("key3", "String").putNull("key4"));
        assertThat(jsonObject.getInteger("key1")).isEqualTo(1);
        assertThat(jsonObject.getBoolean("key2")).isEqualTo(true);
        assertThat(jsonObject.getString("key3")).isEqualTo("String");
        assertThat(jsonObject.getValue("key4")).isNull();
        assertThat(jsonObject.containsKey("key1")).isEqualTo(true);
        assertThat(jsonObject.containsKey("keyX")).isEqualTo(false);
        assertThat(jsonObject.encode()).isEqualTo("{\"key1\":1,\"key2\":true,\"key3\":\"String\",\"key4\":null}");
        assertThat(jsonObject.isEmpty()).isEqualTo(false);
        assertThat(jsonObject.size()).isEqualTo(4);
    }

    @Test
    void testGetComplexValues() {
        ImmutableJsonObject jsonObject = new ImmutableJsonObject(new JsonObject()
                .put("object", new JsonObject().put("key1", 1).put("key2", true).put("key3", "String").putNull("key4"))
                .put("array", new JsonArray().add(1).add(2).add(3)));

        assertThat(jsonObject.getJsonObject("object").getMap()).containsExactly("key1", 1, "key2", true, "key3",
                "String", "key4", null);
        // also check that the returned complex type is immutable
        assertThrows(UnsupportedOperationException.class, () -> jsonObject.getJsonObject("object").put("keyX", true));
        assertThrows(UnsupportedOperationException.class,
                () -> ((JsonObject) jsonObject.getValue("object")).put("keyX", true));

        assertThat(jsonObject.getJsonArray("array").getList()).containsExactly(1, 2, 3);
        // also check that the returned complex type is immutable
        assertThrows(UnsupportedOperationException.class, () -> jsonObject.getJsonArray("array").add(true));
        assertThrows(UnsupportedOperationException.class, () -> ((JsonArray) jsonObject.getValue("array")).add(true));
    }

    @Test
    void testCopyIsMutable() {
        assertDoesNotThrow(() -> new ImmutableJsonObject().copy().put("keyX", true));
    }
}
