package io.neonbee.test.helper;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

public final class SystemHelper {

    /**
     * This method tries to find a free port on the system and returns it.
     * <p>
     * <b>Attention:</b> It is not guaranteed that the returned port is still free, but the chance is very high.
     *
     * @return A free port on the system
     * @throws IOException Socket could not be created
     */
    public static int getFreePort() throws IOException {
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
        runnable.run();
        SystemHelper.setEnvironment(oldEnvironment);
    }

    private SystemHelper() {
        // Utils class no need to instantiate
    }
}
