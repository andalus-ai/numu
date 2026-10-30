package tech.kayys.numou.ecosystem.application.api.command;

import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyKind;
import tech.kayys.numou.ecosystem.domain.party.PartyStatus;

import java.util.Objects;

public record RegisterPartyResult(PartyId partyId, PartyKind kind, PartyStatus status) {

    public RegisterPartyResult {
        Objects.requireNonNull(partyId, "partyId cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
    }
}
