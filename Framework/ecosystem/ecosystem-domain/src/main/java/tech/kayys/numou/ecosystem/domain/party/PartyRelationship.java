package tech.kayys.syirkah.ecosystem.domain.party;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Directed relationship between two distinct parties (config02.md §P4-11 #15).
 */
public final class PartyRelationship extends AbstractAggregateRoot<PartyRelationshipId> {

    private PartyId sourcePartyId;
    private PartyId targetPartyId;
    private PartyRelationshipType type;
    private PartyRelationshipStatus status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    private PartyRelationship() {
        super();
    }

    private PartyRelationship(PartyRelationshipId id) {
        super(id);
        this.status = PartyRelationshipStatus.PROPOSED;
    }

    public static PartyRelationship establish(
            PartyRelationshipId id,
            PartyId sourcePartyId,
            PartyId targetPartyId,
            PartyRelationshipType type,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(sourcePartyId, "sourcePartyId cannot be null");
        Objects.requireNonNull(targetPartyId, "targetPartyId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom cannot be null");

        if (sourcePartyId.equals(targetPartyId)) {
            throw new BusinessRuleViolation("Self-relationships are prohibited: source and target party cannot be identical");
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessRuleViolation("effectiveTo cannot be before effectiveFrom");
        }

        PartyRelationship rel = new PartyRelationship(id);
        rel.sourcePartyId = sourcePartyId;
        rel.targetPartyId = targetPartyId;
        rel.type = type;
        rel.effectiveFrom = effectiveFrom;
        rel.effectiveTo = effectiveTo;
        rel.status = PartyRelationshipStatus.ACTIVE;
        return rel;
    }

    public void terminate(LocalDate terminationDate) {
        if (status == PartyRelationshipStatus.TERMINATED) {
            return;
        }
        this.status = PartyRelationshipStatus.TERMINATED;
        this.effectiveTo = terminationDate != null ? terminationDate : LocalDate.now();
    }

    public PartyId sourcePartyId() {
        return sourcePartyId;
    }

    public PartyId targetPartyId() {
        return targetPartyId;
    }

    public PartyRelationshipType type() {
        return type;
    }

    public PartyRelationshipStatus status() {
        return status;
    }

    public LocalDate effectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate effectiveTo() {
        return effectiveTo;
    }

    public boolean isCurrentlyEffective(LocalDate asOf) {
        if (status != PartyRelationshipStatus.ACTIVE) {
            return false;
        }
        if (asOf.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !asOf.isAfter(effectiveTo);
    }
}
