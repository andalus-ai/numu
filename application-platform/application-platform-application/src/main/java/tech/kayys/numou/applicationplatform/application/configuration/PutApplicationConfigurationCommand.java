package tech.kayys.numou.applicationplatform.application.configuration;

import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;

import java.util.Objects;

/** expectedRevision=0 creates the initial tenant configuration; positive values update by CAS. */
public record PutApplicationConfigurationCommand(ApplicationId applicationId, String schemaVersion,
        String payloadJson, long expectedRevision) {
    public PutApplicationConfigurationCommand {
        Objects.requireNonNull(applicationId, "applicationId");
        if (schemaVersion == null || schemaVersion.isBlank())
            throw new IllegalArgumentException("schemaVersion is required");
        if (payloadJson == null || payloadJson.isBlank())
            throw new IllegalArgumentException("payloadJson is required");
        if (expectedRevision < 0) throw new IllegalArgumentException("expectedRevision cannot be negative");
    }
}
