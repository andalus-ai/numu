package tech.kayys.syirkah.ecosystem.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.syirkah.ecosystem.domain.identifier.RelationshipId;
import tech.kayys.syirkah.ecosystem.domain.valueobject.RelationshipStatus;
import tech.kayys.syirkah.ecosystem.domain.valueobject.RelationshipType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A directed relationship between two participants
 * (Docs/Plan/base00.md §6, base01.md §6 and §P1-09).
 *
 * <p>Without this, every domain invents its own "customer", "supplier" and
 * "carrier" tables. With it, the ecosystem answers "who trades with whom,
 * in which direction, since when" once - and each domain references the
 * relationship instead of duplicating the parties.
 */
public final class ParticipantRelationship extends AbstractAggregateRoot<RelationshipId> {

    private static final long serialVersionUID = 1L;

    private ParticipantId sourceParticipantId;
    private ParticipantId targetParticipantId;
    private RelationshipType type;
    private RelationshipStatus status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    private ParticipantRelationship() {
        super();
    }

    private ParticipantRelationship(RelationshipId id) {
        super(id);
        this.status = RelationshipStatus.PROPOSED;
    }

    /**
     * Proposes a relationship from {@code source} to {@code target}.
     *
     * <p>Self-relationships are rejected: a relationship needs two distinct
     * participants to have any business meaning.
     */
    public static ParticipantRelationship propose(
            RelationshipId id,
            ParticipantId sourceParticipantId,
            ParticipantId targetParticipantId,
            RelationshipType type,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(sourceParticipantId, "sourceParticipantId cannot be null");
        Objects.requireNonNull(targetParticipantId, "targetParticipantId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        if (sourceParticipantId.equals(targetParticipantId)) {
            throw new BusinessRuleViolation(
                    "A participant cannot hold a relationship with itself"
            );
        }
        if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessRuleViolation(
                    "Relationship end date cannot precede its start date"
            );
        }

        final var relationship = new ParticipantRelationship(id);
        relationship.sourceParticipantId = sourceParticipantId;
        relationship.targetParticipantId = targetParticipantId;
        relationship.type = type;
        relationship.effectiveFrom = effectiveFrom;
        relationship.effectiveTo = effectiveTo;
        return relationship;
    }

    /** Rehydrates a relationship from persistence. */
    public static ParticipantRelationship rehydrate(
            RelationshipId id,
            ParticipantId sourceParticipantId,
            ParticipantId targetParticipantId,
            RelationshipType type,
            RelationshipStatus status,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        final var relationship = new ParticipantRelationship(id);
        relationship.sourceParticipantId = sourceParticipantId;
        relationship.targetParticipantId = targetParticipantId;
        relationship.type = type;
        relationship.status = status;
        relationship.effectiveFrom = effectiveFrom;
        relationship.effectiveTo = effectiveTo;
        return relationship;
    }

    /** Puts the relationship into force. */
    public void activate() {
        if (status == RelationshipStatus.TERMINATED) {
            throw new InvalidStateException("A terminated relationship cannot be reactivated");
        }
        this.status = RelationshipStatus.ACTIVE;
        touch();
    }

    /** Temporarily blocks usage while preserving history. */
    public void suspend() {
        if (status != RelationshipStatus.ACTIVE) {
            throw new InvalidStateException(
                    "Only an active relationship can be suspended, was " + status
            );
        }
        this.status = RelationshipStatus.SUSPENDED;
        touch();
    }

    /** Ends the relationship permanently. */
    public void terminate(LocalDate endDate) {
        if (status == RelationshipStatus.TERMINATED) {
            throw new InvalidStateException("Relationship is already terminated");
        }
        this.status = RelationshipStatus.TERMINATED;
        this.effectiveTo = endDate;
        touch();
    }

    public boolean isUsableOn(LocalDate date) {
        if (status != RelationshipStatus.ACTIVE) {
            return false;
        }
        if (date == null) {
            return true;
        }
        final boolean afterStart = effectiveFrom == null || !date.isBefore(effectiveFrom);
        final boolean beforeEnd = effectiveTo == null || !date.isAfter(effectiveTo);
        return afterStart && beforeEnd;
    }

    public ParticipantId getSourceParticipantId() {
        return sourceParticipantId;
    }

    public ParticipantId getTargetParticipantId() {
        return targetParticipantId;
    }

    public RelationshipType getType() {
        return type;
    }

    public RelationshipStatus getStatus() {
        return status;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    private void touch() {
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "ParticipantRelationship{id=" + getId()
                + ", " + sourceParticipantId + " " + type + " " + targetParticipantId
                + ", status=" + status
                + '}';
    }
}
