package io.neonbee.internal.buffer;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Objects;

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Unfortunately our {@link ImmutableBuffer} class can only <em>mimic</em> to be a real {@link BufferImpl}, as some
 * methods in {@link BufferImpl}, like for example {@link BufferImpl#appendBuffer} and {@link BufferImpl#setBuffer}
 * internally case to {@link BufferImpl} and <em>not</em> to the {@link Buffer} interface, this class
 * <strong>cannot</strong> be used interchangeably in all use cases! Thus it is important that when calling
 * {@code buffer.appendBuffer(immutableBuffer);} or {@code buffer.setBuffer(immutableBuffer);} on any other buffer, to
 * instead be calling {@code buffer.appendBuffer(immutableBuffer.getBuffer());} or
 * {@code buffer.setBuffer(immutableBuffer.getBuffer());}. Other attempts will result in a {@link ClassCastException}.
 *
 * This issue gets fixed with https://github.com/eclipse-vertx/vert.x/pull/4111.
 */
@SuppressWarnings({ "PMD.ExcessivePublicCount", "PMD.CyclomaticComplexity", "PMD.TooManyMethods", "PMD.GodClass" })
public final class ImmutableBuffer implements Buffer {
    /**
     * An empty immutable buffer.
     */
    public static final ImmutableBuffer EMPTY;

    static {
        EMPTY = new ImmutableBuffer();
    }

    private final Buffer buffer;

    /**
     * Returns an empty instance of {@link ImmutableBuffer}. Note that the same instance is returned, so
     * {@code ImmutableBuffer.buffer() == ImmutableBuffer.buffer()}.
     *
     * @return an empty instance of {@link ImmutableBuffer}
     */
    public static ImmutableBuffer buffer() {
        return EMPTY;
    }

    /**
     * Creates an immutable instance out of any {@link Buffer}. In case an immutable instance is passed, the same
     * immutable instance will be returned by this method. So
     * {@code ImmutableBuffer.buffer(immutableBuffer) == immutableBuffer}, while
     * {@code ImmutableBuffer.buffer(buffer) != buffer}, however {@code ImmutableBuffer.buffer(buffer).equals(buffer)}.
     *
     * @param buffer the buffer to make immutable
     * @return an immutable facade to the buffer
     */
    public static ImmutableBuffer buffer(Buffer buffer) {
        return buffer instanceof ImmutableBuffer ? (ImmutableBuffer) buffer : new ImmutableBuffer(buffer);
    }

    /**
     * Creates a new empty immutable {@link Buffer}. In case no new instance is required use {@link #EMPTY} instead.
     */
    ImmutableBuffer() {
        this(EMPTY_BUFFER);
    }

    /**
     * Wraps an existing {@link Buffer} into an immutable facade.
     *
     * @param buffer the buffer to wrap
     */
    ImmutableBuffer(Buffer buffer) {
        this(Objects.requireNonNull(buffer), buffer.getByteBuf());
    }

    /**
     * Wraps an existing Netty {@link ByteBuf} into an immutable facade.
     *
     * @param byteBuffer the Netty byte buffer to wrap
     */
    ImmutableBuffer(ByteBuf byteBuffer) {
        this(Buffer.buffer(byteBuffer), byteBuffer);
    }

    /**
     * Small optimization, as calling {@link Buffer#getByteBuf} will duplicate the underlying buffer.
     *
     * @param buffer     the buffer to wrap
     * @param byteBuffer the associated Netty byte-buffer
     */
    private ImmutableBuffer(Buffer buffer, ByteBuf byteBuffer) {
        // if the underlying byte buffer is read-only already, there is no need to make it any more immutable
        this.buffer = byteBuffer.isReadOnly() ? buffer : Buffer.buffer(byteBuffer.asReadOnly());
    }

    /**
     * This method is necessary in order to use this buffer when for instance calling
     * {@link Buffer#appendBuffer(Buffer)} or {@link Buffer#setBuffer(int, Buffer)} on any other buffer. See the JavaDoc
     * of {@link ImmutableBuffer} for a more detailed explanation. The underlying {@link Buffer} is still immutable, as
     * the underlying Netty {@link ByteBuf} was casted to an immutable instance by the benefit of the class, of not
     * having to copy the buffer when sending it via the event bus, as in order to not violate the immutable nature this
     * method would actually have to copy the buffer before returning it.
     *
     * @return the internal {@link Buffer} which can (likely) be cast into a {@link BufferImpl}
     */
    public Buffer getBuffer() {
        return buffer;
    }

    @Override
    public Buffer getBuffer(int start, int end) {
        return buffer.getBuffer(start, end);
    }

    @Override
    public void writeToBuffer(Buffer buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int readFromBuffer(int pos, Buffer buffer) {
        return buffer.readFromBuffer(pos, buffer);
    }

    @Override
    public JsonObject toJsonObject() {
        return buffer.toJsonObject();
    }

    @Override
    public JsonArray toJsonArray() {
        return buffer.toJsonArray();
    }

    @Override
    public byte getByte(int pos) {
        return buffer.getByte(pos);
    }

    @Override
    public short getUnsignedByte(int pos) {
        return buffer.getUnsignedByte(pos);
    }

    @Override
    public int getInt(int pos) {
        return buffer.getInt(pos);
    }

    @Override
    public int getIntLE(int pos) {
        return buffer.getIntLE(pos);
    }

    @Override
    public long getUnsignedInt(int pos) {
        return buffer.getUnsignedInt(pos);
    }

    @Override
    public long getUnsignedIntLE(int pos) {
        return buffer.getUnsignedIntLE(pos);
    }

    @Override
    public long getLong(int pos) {
        return buffer.getLong(pos);
    }

    @Override
    public long getLongLE(int pos) {
        return buffer.getLongLE(pos);
    }

    @Override
    public double getDouble(int pos) {
        return buffer.getDouble(pos);
    }

    @Override
    public float getFloat(int pos) {
        return buffer.getFloat(pos);
    }

    @Override
    public short getShort(int pos) {
        return buffer.getShort(pos);
    }

    @Override
    public short getShortLE(int pos) {
        return buffer.getShortLE(pos);
    }

    @Override
    public int getUnsignedShort(int pos) {
        return buffer.getUnsignedShort(pos);
    }

    @Override
    public int getUnsignedShortLE(int pos) {
        return buffer.getUnsignedShortLE(pos);
    }

    @Override
    public int getMedium(int pos) {
        return buffer.getMedium(pos);
    }

    @Override
    public int getMediumLE(int pos) {
        return buffer.getMediumLE(pos);
    }

    @Override
    public int getUnsignedMedium(int pos) {
        return buffer.getUnsignedMedium(pos);
    }

    @Override
    public int getUnsignedMediumLE(int pos) {
        return buffer.getUnsignedMediumLE(pos);
    }

    @Override
    public byte[] getBytes() {
        return buffer.getBytes();
    }

    @Override
    public byte[] getBytes(int start, int end) {
        return buffer.getBytes(start, end);
    }

    @Override
    public ImmutableBuffer getBytes(byte[] dst) {
        buffer.getBytes(dst);
        return this;
    }

    @Override
    public ImmutableBuffer getBytes(byte[] dst, int dstIndex) {
        buffer.getBytes(dst, dstIndex);
        return this;
    }

    @Override
    public ImmutableBuffer getBytes(int start, int end, byte[] dst) {
        buffer.getBytes(start, end, dst);
        return this;
    }

    @Override
    public ImmutableBuffer getBytes(int start, int end, byte[] dst, int dstIndex) {
        buffer.getBytes(start, end, dst, dstIndex);
        return this;
    }

    @Override
    public String getString(int start, int end, String enc) {
        return buffer.getString(start, end, enc);
    }

    @Override
    public String getString(int start, int end) {
        return buffer.getString(start, end);
    }

    @Override
    public ImmutableBuffer appendBuffer(Buffer buff) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendBuffer(Buffer buff, int offset, int len) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendBytes(byte[] bytes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendBytes(byte[] bytes, int offset, int len) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendByte(byte b) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendUnsignedByte(short b) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendInt(int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendIntLE(int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendUnsignedInt(long i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendUnsignedIntLE(long i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendMedium(int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendMediumLE(int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendLong(long l) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendLongLE(long l) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendShort(short s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendShortLE(short s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendUnsignedShort(int s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendUnsignedShortLE(int s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendFloat(float f) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendDouble(double d) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendString(String str, String enc) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer appendString(String str) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setByte(int pos, byte b) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setUnsignedByte(int pos, short b) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setInt(int pos, int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setIntLE(int pos, int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setUnsignedInt(int pos, long i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setUnsignedIntLE(int pos, long i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setMedium(int pos, int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setMediumLE(int pos, int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setLong(int pos, long l) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setLongLE(int pos, long l) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setDouble(int pos, double d) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setFloat(int pos, float f) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setShort(int pos, short s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setShortLE(int pos, short s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setUnsignedShort(int pos, int s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setUnsignedShortLE(int pos, int s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setBuffer(int pos, Buffer b) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setBuffer(int pos, Buffer b, int offset, int len) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setBytes(int pos, ByteBuffer b) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setBytes(int pos, byte[] b) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setBytes(int pos, byte[] b, int offset, int len) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setString(int pos, String str) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBuffer setString(int pos, String str, String enc) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int length() {
        return buffer.length();
    }

    @Override
    public ImmutableBuffer slice() {
        return this;
    }

    @Override
    public ImmutableBuffer slice(int start, int end) {
        return new ImmutableBuffer(buffer.slice(start, end));
    }

    @Override
    public ByteBuf getByteBuf() {
        return buffer.getByteBuf();
    }

    @Override
    public ImmutableBuffer copy() {
        return this;
    }

    @Override
    // this method violates the symmetric property of how equal should be implement, because as of how BufferImpl
    // implements equal, it is impossible to fulfill this property. our aim is that buffers with the same content equal
    // each other, regardless of it's content, so buffer.equal(immutableBuffer) will never be true, while
    // immutableBuffer.equal(buffer) and immutableBuffer.equal(immutableBuffer) will, as long as the content is equal
    public boolean equals(Object other) {
        return buffer.equals(other instanceof ImmutableBuffer ? ((ImmutableBuffer) other).buffer : other);
    }

    @Override
    public int hashCode() {
        return buffer.hashCode();
    }

    @Override
    public String toString() {
        return buffer.toString();
    }

    @Override
    public String toString(String enc) {
        return buffer.toString(enc);
    }

    @Override
    public String toString(Charset enc) {
        return buffer.toString(enc);
    }

    /**
     * Creates a mutable copy of this buffer.
     *
     * @return the mutable copy
     */
    public Buffer mutableCopy() {
        return buffer.length() == 0 ? Buffer.buffer() : buffer.copy();
    }
}
