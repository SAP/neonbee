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

    // store the source object to be able to compare it
    private final JsonObject object;

    /**
     * Create a new, empty instance.
     */
    public ImmutableJsonObject() {
        this(emptyMap());
    }

    /**
     * Create an instance from a string of JSON.
     *
     * @param json the string of JSON
     */
    public ImmutableJsonObject(String json) {
        this(new JsonObject(json));
    }

    /**
     * Create an instance from a Map. The Map is not copied.
     *
     * @param map the map to create the instance from.
     */
    public ImmutableJsonObject(Map<String, Object> map) {
        this(new JsonObject(map));
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
        super(Collections.unmodifiableMap(obj.getMap()));
        this.object = obj instanceof ImmutableJsonObject ? ((ImmutableJsonObject) obj).object : obj;
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

    @Override
    public ImmutableJsonObject copy() {
        return this;
    }

    @Override
    // this method violates the symmetric property of how equal should be implement, because as of how JsonObject
    // implements equal, it is impossible to fulfill this property. our aim is that object with the same content equal
    // each other, so object.equal(immutableObject) will never be true, while immutableObject.equal(object) and
    // immutableObject.equal(immutableObject) will, as long as the content is equal
    public boolean equals(Object other) {
        return object.equals(other instanceof ImmutableJsonObject ? ((ImmutableJsonObject) other).object : other);
    }

    @Override
    public int hashCode() {
        return object.hashCode();
    }

    /**
     * Creates a mutable copy of this object.
     *
     * @return The mutable copy of this object
     */
    public JsonObject mutableCopy() {
        return new JsonObject(getMap()).copy();
    }
}
