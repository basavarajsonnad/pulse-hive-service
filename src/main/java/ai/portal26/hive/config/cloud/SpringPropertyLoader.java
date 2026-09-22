package ai.portal26.hive.config.cloud;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.lang.NonNull;

/**
 * Wraps AppPropertyLoader into a Spring-compatible PropertySource.
 * <p>
 * Ported from Portal26's platform-wide in-pod loader (originally
 * {@code ai.portal26.gateway.config.cloud.SpringPropertyLoader} in pulse-partner-gateway). The
 * only change from the source it was ported from: {@code MapperUtils.isNullOrEmpty(Object)}
 * (from the private {@code com.titaniamlabs:engine-obf} artifact, not available outside
 * Portal26) is replaced with an inline null/empty check.
 * <p>
 * <b>Note:</b> This class is NOT a Spring component - it's manually instantiated by
 * CloudPropertiesFacade based on active profiles.
 */
public class SpringPropertyLoader implements AppPropertyLoader {

    private static final Logger LOG = LoggerFactory.getLogger(SpringPropertyLoader.class);

    public static final String CLOUD_SECRETS_MANAGER_PROPERTY_SOURCE = "CloudSecretsManagerPropertySource";

    private AppPropertyLoader propertyLoader;

    public void setPropertyLoader(AppPropertyLoader propertyLoader) {
        this.propertyLoader = propertyLoader;
    }

    @Override
    public JSONObject fetchProperties() {
        return propertyLoader.fetchProperties();
    }

    @Override
    public PropertySource<JSONObject> fetchPropertySource() {
        JSONObject jsonObject = propertyLoader.fetchProperties();

        if (jsonObject != null && !jsonObject.isEmpty()) {
            LOG.info("Creating Spring PropertySource from AWS SM properties");
            return createPropertySource(jsonObject);
        } else {
            LOG.info("No properties fetched from AWS SM");
            return null;
        }
    }

    /**
     * Creates a Spring-compatible PropertySource from JSON object.
     *
     * @param jsonObject JSON object containing properties
     * @return Spring PropertySource
     */
    public PropertySource<JSONObject> createPropertySource(final JSONObject jsonObject) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Properties loaded from AWS SM: {}", jsonObject.keySet());
        }

        return new EnumerablePropertySource<>(CLOUD_SECRETS_MANAGER_PROPERTY_SOURCE) {

            @Override
            public Object getProperty(@NonNull String name) {
                return jsonObject.optString(name, null);
            }

            @Override
            @NonNull
            public String[] getPropertyNames() {
                return jsonObject.keySet().toArray(new String[0]);
            }
        };
    }
}
