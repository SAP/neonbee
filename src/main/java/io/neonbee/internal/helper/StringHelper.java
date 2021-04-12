package io.neonbee.internal.helper;

public final class StringHelper {
    /**
     * An empty string.
     */
    public static final String EMPTY = "";

    /**
     * This helper class cannot be instantiated.
     */
    private StringHelper() {}

    /**
     * Will be deleted in an upcoming commit, therefore no javadoc. TODO
     *
     * @param string      string
     * @param regex       regex
     * @param replacement replacement
     * @return string
     */
    public static String replaceLast(String string, String regex, String replacement) {
        return string.replaceFirst("(?s)(.*)" + regex, "$1" + replacement);
    }
}
