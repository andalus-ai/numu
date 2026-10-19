package tech.kayys.numou.workforce.domain.worker;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.audit.AuditMeta;
import tech.kayys.numou.foundation.domain.ref.PersonRef;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.worker.event.WorkerActivated;
import tech.kayys.numou.workforce.domain.worker.event.WorkerCreated;
import tech.kayys.numou.workforce.domain.worker.event.WorkerDeactivated;
import tech.kayys.numou.workforce.domain.worker.event.WorkerSuspended;

import java.time.Instant;
import java.util.Objects;

/**
 * Worker aggregate root representing a person capable of performing work in the ecosystem.
 * Maintains core identity, tenant, classification and lifecycle.
 */
public final class Worker extends AbstractAggregateRoot<WorkerId> {

    private final TenantId tenantId;
    private final PersonRef person;
    private final WorkerType type;

    private WorkerStatus status;
    private AuditMeta audit;

    private Worker(
            WorkerId id,
            TenantId tenantId,
            PersonRef person,
            WorkerType type,
            WorkerStatus status,
            AuditMeta audit
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "TenantId must not be null");
        this.person = Objects.requireNonNull(person, "PersonRef must not be null");
        this.type = Objects.requireNonNull(type, "WorkerType must not be null");
        this.status = Objects.requireNonNull(status, "WorkerStatus must not be null");
        this.audit = Objects.requireNonNull(audit, "AuditMeta must not be null");
    }

    public static Worker create(
            WorkerId id,
            TenantId tenantId,
            PersonRef person,
            WorkerType type,
            String actor,
            Instant now
    ) {
        Objects.requireNonNull(id, "WorkerId must not be null");
        Objects.requireNonNull(now, "Timestamp must not be null");

        Worker worker = new Worker(
                id,
                tenantId,
                person,
                type,
                WorkerStatus.ACTIVE,
                AuditMeta.initial(actor, now)
        );
        worker.raise(new WorkerCreated(id, tenantId, person, type));
        return worker;
    }

    public void suspend(String actor, Instant now, String reason) {
        if (status == WorkerStatus.INACTIVE) {
            throw new IllegalStateException("Inactive worker cannot be suspended");
        }
        if (status == WorkerStatus.SUSPENDED) {
            return;
        }

        this.status = WorkerStatus.SUSPENDED;
        this.audit = audit.updated(actor, now);
        this.updatedAt = now;
        incrementVersion();
        raise(new WorkerSuspended(id, reason));
    }

    public void activate(String actor, Instant now) {
        if (status == WorkerStatus.INACTIVE) {
            throw new IllegalStateException("Inactive worker cannot be activated directly");
        }
        if (status == WorkerStatus.ACTIVE) {
            return;
        }

        this.status = WorkerStatus.ACTIVE;
        this.audit = audit.updated(actor, now);
        this.updatedAt = now;
        incrementVersion();
        raise(new WorkerActivated(id));
    }

    public void deactivate(String actor, Instant now, String reason) {
        if (status == WorkerStatus.INACTIVE) {
            return;
        }

        this.status = WorkerStatus.INACTIVE;
        this.audit = audit.updated(actor, now);
        this.updatedAt = now;
        incrementVersion();
        raise(new WorkerDeactivated(id, reason));
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public PersonRef person() {
        return person;
    }

    public WorkerType type() {
        return type;
    }

    public WorkerStatus status() {
        return status;
    }

    public AuditMeta audit() {
        return audit;
    }
}
