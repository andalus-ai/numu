package tech.kayys.numou.applicationplatform.domain.installation;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Tenant-owned installation state. It records activation of an application package;
 * it never owns or deletes the business records of capabilities used by that app.
 */
public final class ApplicationInstallation {
    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");
    private static final Pattern FAILURE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,119}$");

    private final ApplicationInstallationId id;
    private final TenantId tenantId;
    private final ApplicationId applicationId;
    private final PackageVersion packageVersion;
    private final String packageDigest;
    private final Instant createdAt;
    private final String createdBy;
    private ApplicationInstallationStatus status;
    private long revision;
    private Instant updatedAt;
    private String updatedBy;
    private String failureCode;

    private ApplicationInstallation(ApplicationInstallationId id, TenantId tenantId, ApplicationId applicationId,
            PackageVersion packageVersion, String packageDigest, ApplicationInstallationStatus status,
            long revision, Instant createdAt, Instant updatedAt, String createdBy, String updatedBy, String failureCode) {
        this.id = Objects.requireNonNull(id); this.tenantId = Objects.requireNonNull(tenantId);
        this.applicationId = Objects.requireNonNull(applicationId); this.packageVersion = Objects.requireNonNull(packageVersion);
        if (packageDigest == null || !SHA256.matcher(packageDigest).matches())
            throw new IllegalArgumentException("Package digest must be a lowercase SHA-256 hex value");
        this.packageDigest = packageDigest; this.status = Objects.requireNonNull(status);
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
        this.revision = revision; this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt); this.createdBy = requireText(createdBy, "createdBy");
        this.updatedBy = requireText(updatedBy, "updatedBy"); this.failureCode = failureCode;
    }

    public static ApplicationInstallation request(TenantId tenantId, ApplicationId applicationId,
            PackageVersion packageVersion, String packageDigest, String actorId, Instant now) {
        String actor = requireText(actorId, "actorId");
        return new ApplicationInstallation(ApplicationInstallationId.generate(), tenantId, applicationId,
                packageVersion, packageDigest, ApplicationInstallationStatus.REQUESTED, 1,
                now, now, actor, actor, null);
    }

    public ApplicationInstallationTransition requestedEvent(Instant occurredAt) {
        return requestedEvent("", occurredAt);
    }
    public ApplicationInstallationTransition requestedEvent(String correlationId, Instant occurredAt) {
        return new ApplicationInstallationTransition(id, tenantId, applicationId, Optional.empty(),
                ApplicationInstallationStatus.REQUESTED, revision, createdBy, correlationId, occurredAt, "INSTALL_REQUESTED");
    }

    public ApplicationInstallationTransition beginValidation(String actorId, Instant now) {
        return beginValidation(actorId, "", now);
    }
    public ApplicationInstallationTransition beginValidation(String actorId, String correlationId, Instant now) {
        return transition(ApplicationInstallationStatus.VALIDATING, actorId, correlationId, now, "");
    }
    public ApplicationInstallationTransition awaitApproval(String actorId, Instant now) {
        return awaitApproval(actorId, "", now);
    }
    public ApplicationInstallationTransition awaitApproval(String actorId, String correlationId, Instant now) {
        return transition(ApplicationInstallationStatus.AWAITING_APPROVAL, actorId, correlationId, now, "");
    }
    public ApplicationInstallationTransition approve(String actorId, Instant now) {
        return approve(actorId, "", now);
    }
    public ApplicationInstallationTransition approve(String actorId, String correlationId, Instant now) {
        return transition(ApplicationInstallationStatus.PROVISIONING, actorId, correlationId, now,
                "INSTALL_APPROVED", ApplicationInstallationStatus.AWAITING_APPROVAL);
    }
    public ApplicationInstallationTransition beginProvisioning(String actorId, Instant now) {
        return beginProvisioning(actorId, "", now);
    }
    public ApplicationInstallationTransition beginProvisioning(String actorId, String correlationId, Instant now) {
        return transition(ApplicationInstallationStatus.PROVISIONING, actorId, correlationId, now, "",
                ApplicationInstallationStatus.VALIDATING);
    }
    public ApplicationInstallationTransition completeProvisioning(String actorId, Instant now) {
        return completeProvisioning(actorId, "", now);
    }
    public ApplicationInstallationTransition completeProvisioning(String actorId, String correlationId, Instant now) {
        return transition(ApplicationInstallationStatus.INSTALLED, actorId, correlationId, now, "");
    }
    public ApplicationInstallationTransition enable(String actorId, Instant now) {
        return enable(actorId, "", now);
    }
    public ApplicationInstallationTransition enable(String actorId, String correlationId, Instant now) {
        return transition(ApplicationInstallationStatus.ENABLED, actorId, correlationId, now, "");
    }
    public ApplicationInstallationTransition suspend(String actorId, Instant now) {
        return suspend(actorId, "", now);
    }
    public ApplicationInstallationTransition suspend(String actorId, String correlationId, Instant now) {
        return transition(ApplicationInstallationStatus.SUSPENDED, actorId, correlationId, now, "");
    }
    public ApplicationInstallationTransition fail(String actorId, Instant now, String stableFailureCode) {
        return fail(actorId, "", now, stableFailureCode);
    }
    public ApplicationInstallationTransition fail(String actorId, String correlationId, Instant now, String stableFailureCode) {
        if (stableFailureCode == null || !FAILURE_CODE.matcher(stableFailureCode).matches())
            throw new IllegalArgumentException("Failure code must be a stable uppercase code");
        return transition(ApplicationInstallationStatus.FAILED, actorId, correlationId, now, stableFailureCode);
    }
    public ApplicationInstallationTransition retry(String actorId, Instant now) {
        return retry(actorId, "", now);
    }
    public ApplicationInstallationTransition retry(String actorId, String correlationId, Instant now) {
        return transition(ApplicationInstallationStatus.VALIDATING, actorId, correlationId, now, "RETRY_REQUESTED");
    }
    public ApplicationInstallationTransition beginUninstall(String actorId, Instant now) {
        return beginUninstall(actorId, "", now);
    }
    public ApplicationInstallationTransition beginUninstall(String actorId, String correlationId, Instant now) {
        return transition(ApplicationInstallationStatus.UNINSTALLING, actorId, correlationId, now, "");
    }
    public ApplicationInstallationTransition completeUninstall(String actorId, Instant now) {
        return completeUninstall(actorId, "", now);
    }
    public ApplicationInstallationTransition completeUninstall(String actorId, String correlationId, Instant now) {
        return transition(ApplicationInstallationStatus.UNINSTALLED, actorId, correlationId, now, "");
    }

    private ApplicationInstallationTransition transition(ApplicationInstallationStatus target,
            String actorId, String correlationId, Instant now, String reasonCode) {
        return transition(target, actorId, correlationId, now, reasonCode, null);
    }

    private ApplicationInstallationTransition transition(ApplicationInstallationStatus target,
            String actorId, String correlationId, Instant now, String reasonCode,
            ApplicationInstallationStatus requiredFrom) {
        String actor = requireText(actorId, "actorId");
        Objects.requireNonNull(now, "now");
        String correlation = correlationId == null ? "" : correlationId;
        ApplicationInstallationStatus from = status;
        if ((requiredFrom != null && from != requiredFrom) || !allowed(from, target))
            throw new IllegalStateException("Invalid application installation transition: " + from + " -> " + target);
        status = target;
        revision = Math.addExact(revision, 1);
        updatedAt = now;
        updatedBy = actor;
        failureCode = target == ApplicationInstallationStatus.FAILED ? reasonCode : null;
        return new ApplicationInstallationTransition(id, tenantId, applicationId, Optional.of(from),
                target, revision, actor, correlation, now, reasonCode);
    }

    private static boolean allowed(ApplicationInstallationStatus from, ApplicationInstallationStatus to) {
        return switch (to) {
            case VALIDATING -> from == ApplicationInstallationStatus.REQUESTED || from == ApplicationInstallationStatus.FAILED;
            case AWAITING_APPROVAL -> from == ApplicationInstallationStatus.VALIDATING;
            case PROVISIONING -> from == ApplicationInstallationStatus.VALIDATING || from == ApplicationInstallationStatus.AWAITING_APPROVAL;
            case INSTALLED -> from == ApplicationInstallationStatus.PROVISIONING;
            case ENABLED -> from == ApplicationInstallationStatus.INSTALLED || from == ApplicationInstallationStatus.SUSPENDED;
            case SUSPENDED -> from == ApplicationInstallationStatus.ENABLED;
            case FAILED -> from == ApplicationInstallationStatus.REQUESTED || from == ApplicationInstallationStatus.VALIDATING
                    || from == ApplicationInstallationStatus.AWAITING_APPROVAL || from == ApplicationInstallationStatus.PROVISIONING
                    || from == ApplicationInstallationStatus.UNINSTALLING;
            case UNINSTALLING -> from == ApplicationInstallationStatus.INSTALLED || from == ApplicationInstallationStatus.ENABLED
                    || from == ApplicationInstallationStatus.SUSPENDED || from == ApplicationInstallationStatus.FAILED;
            case UNINSTALLED -> from == ApplicationInstallationStatus.UNINSTALLING;
            case REQUESTED -> false;
        };
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    public ApplicationInstallationId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public ApplicationId applicationId() { return applicationId; }
    public PackageVersion packageVersion() { return packageVersion; }
    public String packageDigest() { return packageDigest; }
    public ApplicationInstallationStatus status() { return status; }
    public long revision() { return revision; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public String createdBy() { return createdBy; }
    public String updatedBy() { return updatedBy; }
    public Optional<String> failureCode() { return Optional.ofNullable(failureCode); }
}
