package io.neonbee.internal.helper;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import io.neonbee.logging.LoggingFacade;

public final class HostHelper {
    @VisibleForTesting
    static final String CF_INSTANCE_INTERNAL_IP_ENV_KEY = "CF_INSTANCE_INTERNAL_IP";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static String currentIp;

    /**
     * This helper class cannot be instantiated
     */
    private HostHelper() {}

    @SuppressWarnings({ "PMD.AvoidUsingHardCodedIP", "PMD.NonThreadSafeSingleton" })
    public static String getHostIp() {
        if (currentIp == null) {
            String ip = System.getenv(CF_INSTANCE_INTERNAL_IP_ENV_KEY);
            if (Strings.isNullOrEmpty(ip)) {
                try {
                    ip = InetAddress.getLocalHost().getHostAddress();
                } catch (UnknownHostException e) {
                    ip = "127.0.0.1";
                }
            }
            currentIp = ip;
        }
        return currentIp;
    }

    @SuppressWarnings("PMD.UseLocaleWithCaseConversions")
    public static boolean isMac() {
        try {
            return System.getProperty("os.name").toLowerCase().contains("mac");
        } catch (Exception e) {
            LOGGER.error("Error reading system property 'os.name' to determine the operating system", e);
            return false;
        }
    }
}
