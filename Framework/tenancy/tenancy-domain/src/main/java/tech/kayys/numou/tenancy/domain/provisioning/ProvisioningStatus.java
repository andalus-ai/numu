package tech.kayys.syirkah.tenancy.domain.provisioning;

/**
 * Overall provisioning workflow status for a Tenant.
 * Partial success must be tracked so retries can skip completed steps.
 */
public enum ProvisioningStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    PARTIALLY_COMPLETED
}
