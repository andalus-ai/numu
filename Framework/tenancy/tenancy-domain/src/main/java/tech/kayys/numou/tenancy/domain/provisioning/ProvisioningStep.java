package tech.kayys.syirkah.tenancy.domain.provisioning;

/**
 * Individual steps in the tenant provisioning workflow.
 * Each step is idempotent — "ensure X exists" not "create X blindly".
 */
public enum ProvisioningStep {
    TENANT_SETTINGS,
    FEATURE_CONFIGURATION,
    SUBSCRIPTION,
    ACCESS,
    STORAGE,
    APPLICATION_RESOURCES
}
