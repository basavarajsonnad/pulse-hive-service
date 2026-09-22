package ai.portal26.hive.config.cloud;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads application properties from AWS Secrets Manager.
 * <p>
 * Ported from Portal26's platform-wide in-pod loader (originally
 * {@code ai.portal26.gateway.config.cloud.AwsAppPropertyLoader} in pulse-partner-gateway).
 * <p>
 * The only functional change from the source it was ported from: the private
 * {@code com.titaniam.aws.AwsSecretsManager} client (from the {@code com.titaniamlabs:engine-obf}
 * artifact, which is not published anywhere this external repo can resolve it) is replaced
 * with direct calls to the public {@code software.amazon.awssdk:secretsmanager} SDK. The
 * prefix-match / merge-across-secrets / duplicate-key-fails / empty-result-fails behavior
 * below is otherwise identical to the source it was ported from.
 * <p>
 * These property key/values become standard Spring key/value pairs that are loaded into the
 * application context.
 * <p>
 * Naming convention for the secretName: pulse.&lt;env&gt;.&lt;service&gt;.properties
 * <br>Example: pulse.dev-test.hive-service.properties
 * <p>
 * <b>Note:</b> This class is NOT a Spring component - it's manually instantiated by
 * CloudPropertiesFacade based on active profiles. For test/local profiles, CloudPropertiesFacade
 * skips instantiation entirely.
 */
public class AwsAppPropertyLoader implements AppPropertyLoader {

    private static final Logger LOG = LoggerFactory.getLogger(AwsAppPropertyLoader.class);

    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final String secretName;
    private final String valueSource;

    /**
     * Default constructor, initializes values from system properties/environment variables.
     */
    public AwsAppPropertyLoader() {
        this(
                PropertyResolver.getProperty(PROPERTY_AWS_REGION),
                PropertyResolver.getProperty(PROPERTY_AWS_ACCESS_KEY),
                PropertyResolver.getProperty(PROPERTY_AWS_SECRET_KEY),
                PropertyResolver.getProperty(PROPERTY_SECRETSMANAGER_KEYNAME),
                VALUE_SOURCE_AWS_SM  // Always use AWS SM source
        );
    }

    /**
     * Constructor for testing/custom configuration.
     *
     * @param region       AWS region, required
     * @param accessKey    AWS access key, optional (uses instance profile/IRSA if null)
     * @param secretKey    AWS secret key, optional (uses instance profile/IRSA if null)
     * @param secretName   AWS SM secret name (prefix), required
     * @param valueSource  source of properties, required ("aws.sm")
     */
    public AwsAppPropertyLoader(String region, String accessKey, String secretKey,
                                 String secretName, String valueSource) {
        this.region = region;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.secretName = secretName;
        this.valueSource = valueSource;

        Objects.requireNonNull(this.region,
                "AWS region cannot be null. Ensure " + PROPERTY_AWS_REGION + " is set as system property or environment variable.");
        Objects.requireNonNull(this.secretName,
                "Secret name cannot be null. Ensure " + PROPERTY_SECRETSMANAGER_KEYNAME + " is set as system property or environment variable.");

        LOG.info("AwsAppPropertyLoader initialized - region: {}, secretName: {}", region, secretName);
    }

    @Override
    public JSONObject fetchProperties() {
        if (isLoadPropertiesFromCloud()) {
            try (SecretsManagerClient secretsManager = instantiateAwsSecretsManager()) {
                return fetchPropertySourceInBulk(secretsManager);
            }
        } else {
            LOG.info("Cloud properties source not enabled - skipping AWS SM fetch");
            return null;
        }
    }

    /**
     * Fetches all secrets that start with each secretName prefix (comma-separated), merges
     * their contents, and fails hard if any prefix matches nothing or the merged result is
     * empty.
     *
     * @param secretsManager AWS SM client
     * @return JSON object with all the properties
     */
    public JSONObject fetchPropertySourceInBulk(SecretsManagerClient secretsManager) {
        LOG.debug("Fetching secrets from AWS SM with name prefix: {}", secretName);

        JSONObject finalProperties = new JSONObject();
        String[] secretPrefixes = secretName.split(",");

        List<SecretListEntry> allSecrets = secretsManager.listSecretsPaginator()
                .stream()
                .flatMap(response -> response.secretList().stream())
                .collect(Collectors.toList());

        Stream.of(secretPrefixes)
                // For each prefix, get all secrets that start with it
                .flatMap(prefix -> {
                    final String trimmed = prefix.trim();
                    List<SecretListEntry> secrets = allSecrets.stream()
                            .filter(secretListEntry -> secretListEntry.name().startsWith(trimmed))
                            .collect(Collectors.toList());

                    if (secrets.isEmpty()) {
                        throw new IllegalStateException("No secrets found for prefix: " + trimmed +
                                ". Ensure the secret exists in AWS Secrets Manager.");
                    }

                    LOG.info("Found {} secret(s) matching prefix: {}", secrets.size(), trimmed);
                    return secrets.stream();
                })
                // Fetch value from AWS SM
                .map(secret -> {
                    LOG.info("[aws sm] Fetching secret: {}", secret.name());
                    return secretsManager.getSecretValue(GetSecretValueRequest.builder()
                                    .secretId(secret.name())
                                    .build())
                            .secretString();
                })
                // Parse JSON
                .map(JSONObject::new)
                // Flatten to key-value entries
                .flatMap(jsonObject -> jsonObject.toMap().entrySet().stream())
                // Collect into final properties
                .forEach(entry -> {
                    // Will throw JSONException for duplicates - this is intentional
                    finalProperties.putOnce(entry.getKey(), entry.getValue());
                    LOG.debug("[aws sm] Loaded property: {}", entry.getKey());
                });

        if (finalProperties.isEmpty()) {
            throw new IllegalStateException("Empty response from AWS Secrets Manager for key: " + secretName);
        }

        LOG.info("[aws sm] Total properties loaded: {}", finalProperties.length());
        return finalProperties;
    }

    /**
     * Instantiate AWS Secrets Manager client.
     */
    public SecretsManagerClient instantiateAwsSecretsManager() {
        LOG.debug("Creating AWS Secrets Manager client for region: {}", region);

        // Access/secret keys are optional - if either is blank, fall back to the default
        // credentials chain (instance profile / IRSA).
        AwsCredentialsProvider credentialsProvider =
                (accessKey == null || accessKey.isEmpty() || secretKey == null || secretKey.isEmpty())
                        ? DefaultCredentialsProvider.create()
                        : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));

        return SecretsManagerClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    /**
     * Returns true if AWS Secrets Manager is enabled.
     */
    public boolean isAwsSecretsManagerEnabled() {
        return VALUE_SOURCE_AWS_SM.equalsIgnoreCase(valueSource);
    }

    /**
     * Returns true if properties should be loaded from cloud.
     */
    public boolean isLoadPropertiesFromCloud() {
        return isAwsSecretsManagerEnabled();
    }
}
