package io.neonbee.internal.json;

import static java.util.Collections.emptyList;

import java.util.Collections;
import java.util.List;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public final class ImmutableJsonArray extends JsonArray {
    /**
     * An empty immutable JSON array.
     */
    public static final ImmutableJsonArray EMPTY = new ImmutableJsonArray();

    /**
     * Create a new instance with the given string.
     *
     * @param json a JsonArray represented as a string
     */
    public ImmutableJsonArray(String json) {
        this(new JsonArray(json));
    }

    /**
     * Create an empty instance.
     */
    public ImmutableJsonArray() {
        this(emptyList());
    }

    /**
     * Create an instance from a List. The List is not copied.
     *
     * @param list The list to be converted to an {@link ImmutableJsonArray}
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ImmutableJsonArray(List list) {
        super(Collections.unmodifiableList(list));
    }

    /**
     * Create an instance from a Buffer of JSON.
     *
     * @param buf the buffer of JSON.
     */
    public ImmutableJsonArray(Buffer buf) {
        this(new JsonArray(buf));
    }

    /**
     * Create an immutable instance from a existing JsonArray. The JsonObject is not copied.
     *
     * @param arr the existing JSON array
     */
    public ImmutableJsonArray(JsonArray arr) {
        this(arr.getList());
    }

    @Override
    public ImmutableJsonObject getJsonObject(int pos) {
        return new ImmutableJsonObject(super.getJsonObject(pos));
    }

    @Override
    public ImmutableJsonArray getJsonArray(int pos) {
        return new ImmutableJsonArray(super.getJsonArray(pos));
    }

    @Override
    public Object getValue(int pos) {
        Object val = super.getValue(pos);
        if (val instanceof JsonObject) {
            val = new ImmutableJsonObject((JsonObject) val);
        } else if (val instanceof JsonArray) {
            val = new ImmutableJsonArray((JsonArray) val);
        }
        return val;
    }

    @Override
    public ImmutableJsonArray copy() {
        return this;
    }

    /**
     * Creates a mutable copy of this array.
     *
     * @return The mutable copy of this array
     */
    public JsonArray mutableCopy() {
        return new JsonArray(getList()).copy();
    }
}
