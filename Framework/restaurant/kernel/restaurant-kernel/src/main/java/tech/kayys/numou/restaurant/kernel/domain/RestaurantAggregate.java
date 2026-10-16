package tech.kayys.syirkah.restaurant.kernel.domain;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.entity.OptimisticLockable;
import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.time.Instant;

/**
 * Base for restaurant aggregates.
 *
 * <p>Adds two things the foundation base does not have:
 * <ul>
 *   <li><b>stored version</b> - the version this instance had when it was loaded. Repositories use it for
 *       optimistic locking ({@code UPDATE ... WHERE version = storedVersion}). It is independent of the
 *       in-memory {@code version}, which increments once per mutation.</li>
 *   <li><b>explicit time</b> - behaviours take {@code now} as a parameter instead of calling
 *       {@code Instant.now()}, so rules such as "no-show only after the grace period" are deterministic to test.</li>
 * </ul>
 */
public abstract class RestaurantAggregate<ID extends DomainId<?>> extends AbstractAggregateRoot<ID>
        implements OptimisticLockable {

    private static final long serialVersionUID = 1L;

    private long storedVersion;

    protected RestaurantAggregate(ID id, Instant now) {
        super(id);
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 0 means "never persisted". */
    public final long storedVersion() {
        return storedVersion;
    }

    /** Called by repositories after load/save only. */
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
