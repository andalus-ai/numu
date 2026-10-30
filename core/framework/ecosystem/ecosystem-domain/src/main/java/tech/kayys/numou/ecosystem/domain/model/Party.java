package tech.kayys.numou.ecosystem.domain.model;

import tech.kayys.numou.ecosystem.domain.event.PartyRegistered;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyKind;
import tech.kayys.numou.ecosystem.domain.party.PartyStatus;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;

import java.time.Instant;
import java.util.Objects;

/**
 * Canonical cross-domain identity for a person or organization.
 *
 * <p>Party owns identity kind and lifecycle only. Names, contact details,
 * organization structure and person-specific attributes belong to their
 * respective profile capabilities.
 */
public final class Party extends AbstractAggregateRoot<PartyId> {

    private static final long serialVersionUID = 1L;

    private PartyKind kind;
    private PartyStatus status;

    private Party() {
        super();
    }

    private Party(PartyId id) {
        super(id);
        this.status = PartyStatus.ACTIVE;
    }

    public static Party register(PartyId id, PartyKind kind) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
        if (kind == PartyKind.UNKNOWN) {
            throw new BusinessRuleViolation("A canonical Party must have kind PERSON or ORGANIZATION");
        }

        Party party = new Party(id);
        party.kind = kind;
        party.raise(PartyRegistered.of(id, kind));
        return party;
    }

    public static Party rehydrate(PartyId id, PartyKind kind, PartyStatus status) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        if (kind == PartyKind.UNKNOWN) {
            throw new BusinessRuleViolation("A persisted Party cannot have kind UNKNOWN");
        }
        Party party = new Party(id);
        party.kind = kind;
        party.status = status;
        return party;
    }

    public void deactivate() {
        if (status == PartyStatus.INACTIVE) {
            throw new InvalidStateException("Party is already inactive");
        }
        status = PartyStatus.INACTIVE;
        touch();
    }

    public void reactivate() {
        if (status == PartyStatus.ACTIVE) {
            throw new InvalidStateException("Party is already active");
        }
        status = PartyStatus.ACTIVE;
        touch();
    }

    public PartyKind kind() {
        return kind;
    }

    public PartyStatus status() {
        return status;
    }

    public boolean isActive() {
        return status == PartyStatus.ACTIVE;
    }

    private void touch() {
        setUpdatedAt(Instant.now());
        incrementVersion();
    }
}
