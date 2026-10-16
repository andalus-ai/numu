package tech.kayys.syirkah.tenancy.domain.provisioning;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.tenancy.domain.provisioning.event.TenantProvisioningCompleted;
import tech.kayys.syirkah.tenancy.domain.provisioning.event.TenantProvisioningFailed;
import tech.kayys.syirkah.tenancy.domain.provisioning.event.TenantProvisioningStarted;
import tech.kayys.syirkah.tenancy.domain.provisioning.event.TenantProvisioningStepCompleted;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * TenantProvisioning aggregate — tracks the durable progress of the
 * tenant initialization workflow.
 *
 * <p>Design principle: each step is idempotent. If a step is already completed
 * it is skipped on retry, allowing safe re-execution after partial failure.
 */
public final class TenantProvisioning extends AbstractAggregateRoot<TenantProvisioningId> {

    private final TenantId tenantId;
    private ProvisioningStatus status;
    private final Set<ProvisioningStep> completedSteps;
    private String failureReason;

    private TenantProvisioning(TenantProvisioningId id, TenantId tenantId) {
        super(id);
        this.tenantId       = tenantId;
        this.status         = ProvisioningStatus.NOT_STARTED;
        this.completedSteps = EnumSet.noneOf(ProvisioningStep.class);
    }

    public static TenantProvisioning start(TenantProvisioningId id, TenantId tenantId) {
        return new TenantProvisioning(id, tenantId);
    }

    public void begin() {
        if (status != ProvisioningStatus.NOT_STARTED) {
            throw new IllegalStateException("Provisioning cannot begin from " + status);
        }
        status = ProvisioningStatus.IN_PROGRESS;
        updatedAt = Instant.now();
        raise(new TenantProvisioningStarted(UUID.randomUUID(), Instant.now(), tenantId));
    }

    public void completeStep(ProvisioningStep step) {
        if (status != ProvisioningStatus.IN_PROGRESS) {
            throw new IllegalStateException("Provisioning is not IN_PROGRESS — cannot complete step");
        }
        completedSteps.add(step);
        updatedAt = Instant.now();
        raise(new TenantProvisioningStepCompleted(UUID.randomUUID(), Instant.now(), tenantId, step));
    }

    public void complete() {
        status = ProvisioningStatus.COMPLETED;
        updatedAt = Instant.now();
        raise(new TenantProvisioningCompleted(UUID.randomUUID(), Instant.now(), tenantId));
    }

    public void fail(String reason) {
        status = ProvisioningStatus.FAILED;
        failureReason = reason;
        updatedAt = Instant.now();
        raise(new TenantProvisioningFailed(UUID.randomUUID(), Instant.now(), tenantId, null, reason));
    }

    public void partiallyComplete(String reason) {
        status = ProvisioningStatus.PARTIALLY_COMPLETED;
        failureReason = reason;
        updatedAt = Instant.now();
    }

    /** Returns true if the given step was already completed (skip on retry). */
    public boolean isCompleted(ProvisioningStep step) {
        return completedSteps.contains(step);
    }

    // ── accessors ────────────────────────────────────────────────────────────

    public TenantId tenantId()                   { return tenantId; }
    public ProvisioningStatus status()           { return status; }
    public Set<ProvisioningStep> completedSteps(){ return Collections.unmodifiableSet(completedSteps); }
    public String failureReason()                { return failureReason; }
}
