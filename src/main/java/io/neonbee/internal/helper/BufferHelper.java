package io.neonbee.internal.helper;

import static io.neonbee.internal.scanner.ClassPathScanner.getClassLoader;

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
     * Read a given resource into a buffer.
     *
     * @param resource The resource to read
     * @return a Vert.x {@link Buffer}
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
            justification = "False positive in Spotbugs, see https://github.com/spotbugs/spotbugs/issues/1338")
    public static Buffer readResourceToBuffer(String resource) {
        ClassLoader classLoader = getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(resource)) {
            return input != null ? inputStreamToBuffer(input) : null;
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
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
            return buffer.length() - position;
        }

        @Override
        @SuppressWarnings("checkstyle:magicnumber")
        public int read() {
            if (position == buffer.length()) {
                return -1;
            }

            // convert to unsigned byte
            return buffer.getByte(position++) & 0xFF;
        }

        @Override
        public int read(byte[] data, int offset, int length) {
            int size = Math.min(data.length, buffer.length() - position);
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
