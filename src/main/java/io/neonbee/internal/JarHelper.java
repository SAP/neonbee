package io.neonbee.internal;

import java.net.URI;

public final class JarHelper {

    /**
     * This method extracts the relative file path of a passed resources located in a jar file. The URI resource path
     * looks like this:
     *
     * <pre>
     * jar:file:///path/to/jarFile!/fileInJar
     * </pre>
     *
     * and the relative file path for this example would be <i>fileInJar</i>
     *
     * @param jarResource The uri of the jar resource
     * @return The file path of the resource inside of the related jar file
     */
    public static String extractFilePath(URI jarResource) {
        String uriString = jarResource.toString();
        return uriString.substring(uriString.lastIndexOf("!/") + 2);
    }

    private JarHelper() {
        // helper class no need to instantiate
    }
}
