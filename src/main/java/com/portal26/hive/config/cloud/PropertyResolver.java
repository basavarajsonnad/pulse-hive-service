package com.portal26.hive.config.cloud;

/**
 * Simple class to resolve properties from System properties or environment variables.
 * <br/>
 * In the context of kubernetes, properties included in deployment scripts
 * come in as environment variables.
 * <br/>
 * Ported from Portal26's platform-wide in-pod loader (originally
 * {@code ai.portal26.gateway.config.cloud.PropertyResolver} in pulse-partner-gateway). The
 * only change from the source it was ported from: {@code MapperUtils.isNullOrEmpty(String)}
 * (from the private {@code com.titaniamlabs:engine-obf} artifact, not available outside
 * Portal26) is replaced with an inline null/empty check.
 */
public class PropertyResolver {

    public static String getProperty(String key) {
        String value = System.getProperty(key);

        if (isNullOrEmpty(value)) {
            value = System.getenv(key);
        }

        return value;
    }

    public static String getProperty(String key, String defaultValue) {
        String value = getProperty(key);

        if (isNullOrEmpty(value)) {
            value = defaultValue;
        }

        return value;
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
