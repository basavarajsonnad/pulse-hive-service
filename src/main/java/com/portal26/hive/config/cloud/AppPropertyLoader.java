package com.portal26.hive.config.cloud;

import org.json.JSONObject;
import org.springframework.core.env.PropertySource;

/**
 * Interface for loading application properties from external sources (AWS Secrets Manager).
 * <p>
 * Ported from Portal26's platform-wide in-pod loader (originally
 * {@code ai.portal26.gateway.config.cloud.AppPropertyLoader} in pulse-partner-gateway) — same
 * mechanism used by every core service on the platform. Package renamed only; move/rename it
 * into your own base package if you'd like, as long as all five classes in this package move
 * together.
 * <p>
 * Loading behavior is controlled by Spring profiles:
 * <ul>
 *     <li><b>test, local profiles:</b> Skips AWS SM - uses local application.yml</li>
 *     <li><b>dev-test, stg, prod profiles:</b> Loads from AWS Secrets Manager</li>
 * </ul>
 */
public interface AppPropertyLoader {

    // Must match the env var names Portal26 sets in deployment/base/deployment.yml and the
    // per-environment kustomize overlays (pulse.secretsmanager.properties.keyname there).
    String PROPERTY_SECRETSMANAGER_KEYNAME = "pulse.secretsmanager.properties.keyname";
    String VALUE_SOURCE_AWS_SM = "aws.sm";

    // Originally resolved via the private engine-obf artifact's AppConstants.Key enum; that
    // artifact isn't available outside Portal26, so the literal property names it produced
    // (documented in the source this was ported from) are inlined here directly.
    String PROPERTY_AWS_REGION = "titaniam.aws.region";
    /**
     * Encrypted value for the access key
     */
    String PROPERTY_AWS_ACCESS_KEY = "titaniam.aws.access.id";
    /**
     * Encrypted value for the secret key
     */
    String PROPERTY_AWS_SECRET_KEY = "titaniam.aws.secret.id";

    /**
     * Fetches properties from external source.
     *
     * @return JSONObject containing properties, or null if not available
     */
    JSONObject fetchProperties();

    /**
     * Fetches properties as Spring PropertySource.
     *
     * @return PropertySource wrapping the properties, or null if not available
     */
    default PropertySource<JSONObject> fetchPropertySource() {
        return null;
    }
}
