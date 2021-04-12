package io.neonbee.internal.helper;

public final class StringHelper {
    public static final String EMPTY = "";

    /**
     * This helper class cannot be instantiated
     */
    private StringHelper() {}

    public static String replaceLast(String string, String regex, String replacement) {
        return string.replaceFirst("(?s)(.*)" + regex, "$1" + replacement);
    }
}
