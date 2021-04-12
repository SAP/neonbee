package io.neonbee.internal.helper;

import static io.neonbee.internal.scanner.ClassPathScanner.getClassLoader;

import java.io.IOException;
import java.io.InputStream;

import io.vertx.core.buffer.Buffer;

public final class BufferHelper {
    private static final int DEFAULT_BUFFER_SIZE = 4096;

    /**
     * This helper class cannot be instantiated
     */
    private BufferHelper() {}

    public static Buffer inputStreamToBuffer(InputStream input) throws IOException {
        return inputStreamToBuffer(input, DEFAULT_BUFFER_SIZE);
    }

    public static Buffer inputStreamToBuffer(InputStream input, int bufferSize) throws IOException {
        byte[] data = new byte[bufferSize];

        int read;
        Buffer buffer = Buffer.buffer();
        while ((read = input.read(data, 0, data.length)) != -1) {
            buffer.appendBytes(data, 0, read);
        }

        return buffer;
    }

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

    public static InputStream bufferToInputSteram(Buffer buffer) {
        return new BufferInputStream(buffer);
    }

    public static class BufferInputStream extends InputStream {
        private Buffer buffer;

        private int position;

        public BufferInputStream(Buffer buffer) {
            super();
            this.buffer = buffer;
        }

        public int getPosition() {
            return position;
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
