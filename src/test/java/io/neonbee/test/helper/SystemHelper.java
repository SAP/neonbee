package io.neonbee.test.helper;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;

public final class SystemHelper {
    // the ephemeral port range, as suggested by the IANA (https://www.iana.org/assignments/port-numbers)
    private static final Range<Integer> PORT_RANGE = Range.closed(49152, 65535);

    private static final Iterator<Integer> PORTS =
            ContiguousSet.create(PORT_RANGE, DiscreteDomain.integers()).iterator();

    /**
     * This method tries to find a free ephemeral port on the system and returns it. This method will never return the
     * same port twice and checks if the port is free before returning it.
     * <p>
     * <b>Attention:</b> It is not guaranteed that the returned port is still free as soon as it gets used, but the
     * chances are very high.
     *
     * @return A free port on the system
     * @throws IOException Socket could not be created
     */
    public static int getFreePort() throws IOException {
        try {
            while (true) {
                try (ServerSocket socket = new ServerSocket(PORTS.next())) {
                    return socket.getLocalPort();
                } catch (IOException e) {
                    if (!e.getMessage().contains("Address already in use")) {
                        return getFallbackPort();
                    }
                }
            }
        } catch (NoSuchElementException e) {
            return getFallbackPort();
        }
    }

    private static int getFallbackPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * This method replaces the JVM wide copy of the system environment with the passed environment map.
     *
     * @param newEnvironment The new environment map
     * @throws Exception Could not change environment
     */
    public static void setEnvironment(Map<String, String> newEnvironment) throws Exception {
        Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
        Map<String, String> theUnmodifiableEnvironment =
                ReflectionHelper.getValueOfPrivateField(processEnvironmentClass, "theUnmodifiableEnvironment");

        Map<String, String> modifiableEnvironment =
                ReflectionHelper.getValueOfPrivateField(theUnmodifiableEnvironment, "m");
        modifiableEnvironment.clear();
        modifiableEnvironment.putAll(newEnvironment);
    }

    /**
     * This method replaces the JVM wide copy of the system environment with the passed environment map, executes the
     * passed runnable, and resets the environment variables to the original value again.
     *
     * @param newEnvironment The new environment map
     * @param runnable       The code that should be executed within the context of passed environment variables
     * @throws Exception Could not change environment
     */
    public static void withEnvironment(Map<String, String> newEnvironment, Runnable runnable) throws Exception {
        Map<String, String> oldEnvironment = Map.copyOf(System.getenv());
        setEnvironment(newEnvironment);
        try {
            runnable.run();
        } finally {
            setEnvironment(oldEnvironment);
        }
    }

    private SystemHelper() {
        // Utils class no need to instantiate
    }
}
