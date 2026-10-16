package tech.kayys.numou.crm.domain.territory;

import tech.kayys.numou.crm.domain.identifier.AccountId;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.crm.domain.event.territory.TerritoryAssignmentCreated;
import tech.kayys.numou.crm.domain.event.territory.TerritoryAssignmentEnded;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Assignment of an account to a territory.
 */
public final class TerritoryAssignment extends AbstractAggregateRoot<TerritoryAssignmentId> {

    private final TerritoryAssignmentId id;
    private final TerritoryId territoryId;
    private final AccountId accountId;
    private final TerritoryAssignmentOrigin origin;
    private TerritoryAssignmentStatus status;
    private final UUID assignedByUserId;
    private final Instant assignedAt;
    private Instant endedAt;

    private TerritoryAssignment(TerritoryAssignmentId id,
                                TerritoryId territoryId,
                                AccountId accountId,
                                TerritoryAssignmentOrigin origin,
                                UUID assignedByUserId,
                                Instant assignedAt) {
        super(id);
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.territoryId = Objects.requireNonNull(territoryId, "territoryId cannot be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
        this.origin = Objects.requireNonNull(origin, "origin cannot be null");
        this.assignedByUserId = Objects.requireNonNull(assignedByUserId, "assignedByUserId cannot be null");
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt cannot be null");
        this.status = TerritoryAssignmentStatus.ACTIVE;
    }

    public static TerritoryAssignment create(TerritoryAssignmentId id,
                                             TerritoryId territoryId,
                                             AccountId accountId,
                                             TerritoryAssignmentOrigin origin,
                                             UUID assignedByUserId,
                                             Instant assignedAt) {
        TerritoryAssignment assignment = new TerritoryAssignment(id, territoryId, accountId, origin, assignedByUserId, assignedAt);
        assignment.raise(TerritoryAssignmentCreated.of(
                assignment.id.value(),
                assignment.territoryId.value(),
                assignment.accountId.value(),
                assignment.origin.name(),
                assignment.assignedByUserId,
                assignment.assignedAt.toEpochMilli()
        ));
        return assignment;
    }

    public static TerritoryAssignment restore(TerritoryAssignmentId id,
                                              TerritoryId territoryId,
                                              AccountId accountId,
                                              TerritoryAssignmentOrigin origin,
                                              TerritoryAssignmentStatus status,
                                              UUID assignedByUserId,
                                              Instant assignedAt,
                                              Instant endedAt) {
        TerritoryAssignment assignment = new TerritoryAssignment(id, territoryId, accountId, origin, assignedByUserId, assignedAt);
        assignment.status = Objects.requireNonNull(status, "status cannot be null");
        assignment.endedAt = endedAt;
        return assignment;
    }

    public TerritoryAssignmentId id() {
        return id;
    }

    public TerritoryId territoryId() {
        return territoryId;
    }

    public AccountId accountId() {
        return accountId;
    }

    public TerritoryAssignmentOrigin origin() {
        return origin;
    }

    public TerritoryAssignmentStatus status() {
        return status;
    }

    public boolean isActive() {
        return status == TerritoryAssignmentStatus.ACTIVE;
    }

    public UUID assignedByUserId() {
        return assignedByUserId;
    }

    public Instant assignedAt() {
        return assignedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public void end(Instant endedAt) {
        if (status != TerritoryAssignmentStatus.ACTIVE) {
            throw new IllegalStateException("Only active assignments can be ended");
        }
        if (endedAt == null || endedAt.isBefore(assignedAt)) {
            throw new IllegalArgumentException("endedAt must be after assignedAt");
        }
        this.endedAt = Objects.requireNonNull(endedAt, "endedAt cannot be null");
        this.status = TerritoryAssignmentStatus.ENDED;
        touch();
        raise(TerritoryAssignmentEnded.of(
                id.value(),
                territoryId.value(),
                accountId.value(),
                origin.name(),
                assignedByUserId,
                assignedAt.toEpochMilli(),
                endedAt.toEpochMilli()
        ));
    }

    private void touch() {
        setUpdatedAt(Instant.now());
        incrementVersion();
    }
}