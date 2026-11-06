package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Host-owned composition boundary for activating/deactivating an installed application.
 *
 * Implementations compose only host-registered capability providers and declarative
 * manifest contributions. They must not load or execute code from an application package.
 * Both operations must be idempotent for the supplied operationId.
 */
public interface ApplicationHostCompositionPort {
    CompletionStage<Void> activate(ActivationCommand command);
    CompletionStage<Void> deactivate(DeactivationCommand command);

    record ActivationCommand(TenantId tenantId, UUID installationId, String applicationId,
            String packageVersion, String packageDigest, UUID operationId,
            String requestedBy, String correlationId, ValidatedApplicationPackage applicationPackage) {
        public ActivationCommand {
            if (tenantId == null || installationId == null || operationId == null || applicationPackage == null)
                throw new IllegalArgumentException("tenant, installation, operation and verified package are required");
            if (blank(applicationId) || blank(packageVersion) || blank(packageDigest) || blank(requestedBy))
                throw new IllegalArgumentException("application identity and requester are required");
            correlationId = correlationId == null ? "" : correlationId;
            if (!applicationPackage.applicationId().value().equals(applicationId)
                    || !applicationPackage.version().value().equals(packageVersion)
                    || !applicationPackage.sha256Digest().equals(packageDigest))
                throw new IllegalArgumentException("verified package does not match activation identity");
        }
    }

    record DeactivationCommand(TenantId tenantId, UUID installationId, String applicationId,
            String packageVersion, String packageDigest, UUID operationId,
            String requestedBy, String correlationId, String reason) {
        public DeactivationCommand {
            if (tenantId == null || installationId == null || operationId == null)
                throw new IllegalArgumentException("tenant, installation and operation are required");
            if (blank(applicationId) || blank(packageVersion) || blank(packageDigest) || blank(requestedBy))
                throw new IllegalArgumentException("application identity and requester are required");
            correlationId = correlationId == null ? "" : correlationId;
            reason = reason == null ? "LIFECYCLE_TRANSITION" : reason;
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
