package tech.kayys.numou.applicationplatform.domain.installation;

public enum ApplicationInstallationStatus {
    REQUESTED,
    VALIDATING,
    AWAITING_APPROVAL,
    PROVISIONING,
    INSTALLED,
    ENABLED,
    SUSPENDED,
    FAILED,
    UNINSTALLING,
    UNINSTALLED
}
