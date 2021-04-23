package io.neonbee.internal.json;

import static java.util.Collections.emptyMap;

import java.util.Collections;
import java.util.Map;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * An immutable implementation of the JsonObject.
 */
public final class ImmutableJsonObject extends JsonObject {
    /**
     * An empty immutable JSON object.
     */
    public static final ImmutableJsonObject EMPTY = new ImmutableJsonObject();

    /**
     * Create an instance from a string of JSON.
     *
     * @param json the string of JSON
     */
    public ImmutableJsonObject(String json) {
        this(new JsonObject(json));
    }

    /**
     * Create a new, empty instance.
     */
    public ImmutableJsonObject() {
        this(emptyMap());
    }

    /**
     * Create an instance from a Map. The Map is not copied.
     *
     * @param map the map to create the instance from.
     */
    public ImmutableJsonObject(Map<String, Object> map) {
        super(Collections.unmodifiableMap(map));
    }

    /**
     * Create an instance from a buffer.
     *
     * @param buf the buffer to create the instance from.
     */
    public ImmutableJsonObject(Buffer buf) {
        this(new JsonObject(buf));
    }

    /**
     * Create an immutable instance from a existing JsonObject. The JsonObject is not copied.
     *
     * @param obj the existing JSON object
     */
    public ImmutableJsonObject(JsonObject obj) {
        this(obj.getMap());
    }

    @Override
    public ImmutableJsonObject getJsonObject(String key) {
        return new ImmutableJsonObject(super.getJsonObject(key));
    }

    @Override
    public ImmutableJsonObject getJsonObject(String key, JsonObject def) {
        return new ImmutableJsonObject(super.getJsonObject(key, def));
    }

    @Override
    public ImmutableJsonArray getJsonArray(String key) {
        return new ImmutableJsonArray(super.getJsonArray(key));
    }

    @Override
    public ImmutableJsonArray getJsonArray(String key, JsonArray def) {
        return new ImmutableJsonArray(super.getJsonArray(key, def));
    }

    @Override
    public Object getValue(String key) {
        Object val = super.getValue(key);
        if (val instanceof JsonObject) {
            val = new ImmutableJsonObject((JsonObject) val);
        } else if (val instanceof JsonArray) {
            val = new ImmutableJsonArray((JsonArray) val);
        }
        return val;
    }

    @Override
    public Object getValue(String key, Object def) {
        Object val = super.getValue(key, def);
        if (val instanceof JsonObject) {
            val = new ImmutableJsonObject((JsonObject) val);
        } else if (val instanceof JsonArray) {
            val = new ImmutableJsonArray((JsonArray) val);
        }
        return val;
    }
}
