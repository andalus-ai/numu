package tech.kayys.syirkah.reservation.domain;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.entity.OptimisticLockable;
import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.time.Instant;

/**
 * Base aggregate root for the reservation bounded context.
 * Provides explicit timestamp management and optimistic-locking stored version tracking.
 */
public abstract class ReservationAggregate<ID extends DomainId<?>> extends AbstractAggregateRoot<ID>
        implements OptimisticLockable {

    private static final long serialVersionUID = 1L;

    private long storedVersion;

    protected ReservationAggregate(ID id, Instant now) {
        super(id);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public final long storedVersion() {
        return storedVersion;
    }

    public final void markStored(long version) {
        this.storedVersion = version;
    }

    protected final void touch(Instant now) {
        incrementVersion();
        this.updatedAt = now;
    }

    protected final void restoreAudit(Instant createdAt, Instant updatedAt, int version) {
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }
}
