package io.neonbee.internal.helper;

import java.io.IOException;
import java.io.InputStream;

import io.vertx.core.buffer.Buffer;

public final class BufferHelper {
    private static final int DEFAULT_BUFFER_SIZE = 4096;

    /**
     * This helper class cannot be instantiated.
     */
    private BufferHelper() {}

    /**
     * Converts a given Java {@link InputStream} into a Vert.x {@link Buffer}.
     *
     * @param input the {@link InputStream} to convert
     * @return a {@link Buffer} encapsulating the given {@link InputStream}
     * @throws IOException an {@link IOException} if the conversion fails
     */
    public static Buffer inputStreamToBuffer(InputStream input) throws IOException {
        return inputStreamToBuffer(input, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Converts a given Java {@link InputStream} into a Vert.x {@link Buffer}.
     *
     * @param input      the {@link InputStream} to convert
     * @param bufferSize the buffer size to use converting the {@link InputStream}
     * @return a {@link Buffer} encapsulating the given {@link InputStream}
     * @throws IOException an {@link IOException} if the conversion fails
     */
    public static Buffer inputStreamToBuffer(InputStream input, int bufferSize) throws IOException {
        byte[] data = new byte[bufferSize];

        int read;
        Buffer buffer = Buffer.buffer();
        while ((read = input.read(data, 0, data.length)) != -1) {
            buffer.appendBytes(data, 0, read);
        }

        return buffer;
    }

    /**
     * A specific implementation for converting a Vert.x {@link Buffer} into a Java {@link InputStream}.
     */
    public static class BufferInputStream extends InputStream {
        private Buffer buffer;

        private int position;

        /**
         * Create a new {@link BufferInputStream} based on a Vert.x {@link Buffer}.
         *
         * @param buffer the buffer to convert to a {@link InputStream}
         */
        public BufferInputStream(Buffer buffer) {
            super();
            this.buffer = buffer;
        }

        @Override
        public int available() {
            // treat a null buffer as empty to avoid NPEs when upstream passes null
            return buffer == null ? 0 : buffer.length() - position;
        }

        @Override
        @SuppressWarnings("checkstyle:magicnumber")
        public int read() {
            // if buffer is null or already fully consumed, signal end of stream
            if (buffer == null || position == buffer.length()) {
                return -1;
            }

            // convert to unsigned byte
            return buffer.getByte(position++) & 0xFF;
        }

        @Override
        public int read(byte[] data, int offset, int length) {
            // if buffer is null, there is nothing to read
            if (buffer == null) {
                return -1;
            }

            int size = Math.min(Math.min(data.length, length), buffer.length() - position);
            if (size == 0) {
                return -1;
            }

            buffer.getBytes(position, position + size, data, offset);
            position += size;

            return size;
        }

        @SuppressWarnings("PMD.NullAssignment")
        @Override
        public void close() {
            buffer = null;
        }
    }
}
