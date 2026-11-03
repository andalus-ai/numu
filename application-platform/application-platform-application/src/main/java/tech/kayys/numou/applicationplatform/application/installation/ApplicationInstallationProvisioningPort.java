package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Host-owned, idempotent capability provisioning boundary.
 * Implementations must converge on the desired state for the same operationId and
 * must not grant manifest-requested permissions without the host authorization policy.
 */
@FunctionalInterface
public interface ApplicationInstallationProvisioningPort {
    CompletionStage<Void> ensureProvisioned(ProvisioningCommand command);

    record ProvisioningCommand(TenantId tenantId, UUID installationId, String applicationId,
            String packageVersion, String packageDigest, UUID operationId,
            String requestedBy, String correlationId, ValidatedApplicationPackage applicationPackage) {
        public ProvisioningCommand {
            if (tenantId == null || installationId == null || operationId == null || applicationPackage == null)
                throw new IllegalArgumentException("tenant, installation, operation and validated package are required");
            if (applicationId == null || packageVersion == null || packageDigest == null || requestedBy == null)
                throw new IllegalArgumentException("installation identity and requester are required");
            correlationId = correlationId == null ? "" : correlationId;
            if (!applicationPackage.applicationId().value().equals(applicationId)
                    || !applicationPackage.version().value().equals(packageVersion)
                    || !applicationPackage.sha256Digest().equals(packageDigest))
                throw new IllegalArgumentException("provisioning package does not match the committed installation");
        }
    }
}
