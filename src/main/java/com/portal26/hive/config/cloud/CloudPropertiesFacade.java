package com.portal26.hive.config.cloud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

/**
 * Loads properties from AWS Secrets Manager into the Spring Environment at boot.
 * <p>
 * Ported from Portal26's platform-wide in-pod loader (originally
 * {@code ai.portal26.gateway.config.cloud.CloudPropertiesFacade} in pulse-partner-gateway) —
 * the same in-pod loader pattern used by every core service on the platform. Registered as an
 * {@link EnvironmentPostProcessor} via {@code META-INF/spring.factories}.
 * <p>
 * The only functional change from the source it was ported from: {@link AwsAppPropertyLoader}
 * now calls the public {@code software.amazon.awssdk:secretsmanager} SDK directly instead of
 * Portal26's private {@code com.titaniamlabs:engine-obf} artifact (which this external repo
 * cannot depend on — it's only published to a GitHub Packages registry scoped to Portal26's
 * own org). Fetch/merge/failure behavior is otherwise identical.
 * <p>
 * The property source is added with {@link org.springframework.core.env.MutablePropertySources#addLast
 * addLast}, i.e. LOWEST precedence: any value already defined by application.yml or a real env var wins
 * over the Secrets Manager value. <b>Do not declare your Secrets Manager-backed
 * {@code @ConfigurationProperties} fields with an empty {@code ${VAR:}} placeholder default in
 * application.yml/properties</b> — an empty default still counts as a defined value at higher
 * precedence and will silently shadow the Secrets Manager value. See README "Secrets Manager".
 * <p>
 * <b>Profile-based behavior:</b>
 * <ul>
 *     <li><b>test, local profiles:</b> Skips AWS SM loading - uses local application.yml (keeps the
 *         unit/integration suite green without any AWS access)</li>
 *     <li><b>dev-test, stg, prod profiles:</b> Loads properties from AWS Secrets Manager</li>
 * </ul>
 * <p>
 * Steps to enable in a deployed environment:
 * <ol>
 *     <li>Portal26 creates the secret(s) in AWS Secrets Manager following convention:
 *         pulse.&lt;env&gt;.&lt;service&gt;.properties</li>
 *     <li>Portal26 sets these as container env vars (see deployment/base/deployment.yml and
 *         the per-environment kustomize overlays):
 *         <ul>
 *             <li>titaniam.aws.region (e.g., "us-east-2")</li>
 *             <li>titaniam.aws.access.id (optional, uses instance profile/IRSA if not set)</li>
 *             <li>titaniam.aws.secret.id (optional, uses instance profile/IRSA if not set)</li>
 *             <li>pulse.secretsmanager.properties.keyname (comma-separated secret name prefixes to load)</li>
 *         </ul>
 *     </li>
 * </ol>
 */
public class CloudPropertiesFacade implements EnvironmentPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(CloudPropertiesFacade.class);

    // Profiles that skip AWS Secrets Manager loading (use local properties instead)
    private static final List<String> LOCAL_PROFILES = Arrays.asList("test", "local");

    private AppPropertyLoader propertyLoader;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        LOG.info("CloudPropertiesFacade: Checking if AWS Secrets Manager loading is needed");

        // Check active profiles
        String[] activeProfiles = environment.getActiveProfiles();
        LOG.info("Active profiles: {}", Arrays.toString(activeProfiles));

        // Skip AWS SM loading for test/local profiles
        if (shouldSkipAwsSecretsManager(activeProfiles)) {
            LOG.info("Skipping AWS Secrets Manager - using local profiles: {}", Arrays.toString(activeProfiles));
            LOG.info("Application will use properties from application.yml / application-{profile}.yml");
            return;
        }

        LOG.info("Loading properties from AWS Secrets Manager");

        initPropertyLoader();

        PropertySource<JSONObject> propertySource = propertyLoader.fetchPropertySource();

        if (propertySource != null) {
            LOG.info("Adding AWS SM properties to Spring environment");
            environment.getPropertySources().addLast(propertySource);
            LOG.info("AWS SM properties loaded successfully");
        } else {
            LOG.info("No properties found in AWS SM - skipping");
        }
    }

    /**
     * Determines if AWS Secrets Manager loading should be skipped based on active profiles.
     *
     * @param activeProfiles Active Spring profiles
     * @return true if AWS SM should be skipped (local/test profiles), false otherwise
     */
    private boolean shouldSkipAwsSecretsManager(String[] activeProfiles) {
        if (activeProfiles == null || activeProfiles.length == 0) {
            // No profiles active - load from AWS SM (production default)
            return false;
        }

        // Skip AWS SM if any local profile is active
        for (String profile : activeProfiles) {
            if (LOCAL_PROFILES.contains(profile.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Initializes the property loader if not already initialized.
     * This avoids bootstrap dependency for auto-wiring.
     */
    void initPropertyLoader() {
        if (propertyLoader == null) {
            LOG.info("Initializing property loader (not auto-wired)");

            SpringPropertyLoader springPropertyLoader = new SpringPropertyLoader();
            springPropertyLoader.setPropertyLoader(new AwsAppPropertyLoader());

            this.propertyLoader = springPropertyLoader;

            LOG.info("Property loader initialized");
        }
    }
}
