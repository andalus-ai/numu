package tech.kayys.numou.applicationplatform.application.configuration;

import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;

import java.util.concurrent.CompletionStage;

/**
 * Validates and normalizes tenant configuration against the schema declared by
 * the exact installed application package version.
 *
 * <p>Implementations must reject unknown schema versions, invalid values, and
 * inline secret material. Secret values are supplied by a separate secret
 * resolver at runtime; only opaque references may appear in persisted config.</p>
 */
@FunctionalInterface
public interface ApplicationConfigurationSchemaPort {
    CompletionStage<ValidatedConfiguration> validate(
            ApplicationId applicationId, String schemaVersion, String payloadJson);

    record ValidatedConfiguration(String normalizedPayloadJson) {
        public ValidatedConfiguration {
            if (normalizedPayloadJson == null || normalizedPayloadJson.isBlank()
                    || normalizedPayloadJson.length() > 1_048_576)
                throw new IllegalArgumentException("normalized configuration must contain 1..1048576 characters");
        }
    }
}
