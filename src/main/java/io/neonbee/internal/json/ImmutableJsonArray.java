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

    // store the source array to be able to compare it
    private final JsonArray array;

    /**
     * Create an empty instance.
     */
    public ImmutableJsonArray() {
        this(emptyList());
    }

    /**
     * Create a new instance with the given string.
     *
     * @param json a JsonArray represented as a string
     */
    public ImmutableJsonArray(String json) {
        this(new JsonArray(json));
    }

    /**
     * Create an instance from a List. The List is not copied.
     *
     * @param list The list to be converted to an {@link ImmutableJsonArray}
     */
    @SuppressWarnings("rawtypes")
    public ImmutableJsonArray(List list) {
        this(new JsonArray(list));
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
    @SuppressWarnings("unchecked")
    public ImmutableJsonArray(JsonArray arr) {
        super(Collections.unmodifiableList(arr.getList()));
        this.array = arr instanceof ImmutableJsonArray ? ((ImmutableJsonArray) arr).array : arr;
    }

    @Override
    public ImmutableJsonObject getJsonObject(int pos) {
        JsonObject object = super.getJsonObject(pos);
        return object != null ? new ImmutableJsonObject(object) : null;
    }

    @Override
    public ImmutableJsonArray getJsonArray(int pos) {
        JsonArray array = super.getJsonArray(pos);
        return array != null ? new ImmutableJsonArray(array) : null;
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

    @Override
    // this method violates the symmetric property of how equal should be implement, because as of how JsonArray
    // implements equal, it is impossible to fulfill this property. our aim is that arrays with the same content equal
    // each other, so array.equal(immutableArray) will never be true, while immutableArray.equal(array) and
    // immutableArray.equal(immutableArray) will, as long as the content is equal
    public boolean equals(Object other) {
        return array.equals(other instanceof ImmutableJsonArray ? ((ImmutableJsonArray) other).array : other);
    }

    @Override
    public int hashCode() {
        return array.hashCode();
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
