package tech.kayys.numou.ecosystem.application.api.command;

import tech.kayys.numou.ecosystem.domain.party.PartyKind;

import java.util.Objects;

/** Registers a canonical identity before domain-specific profiles are created. */
public record RegisterPartyCommand(PartyKind kind) {

    public RegisterPartyCommand {
        Objects.requireNonNull(kind, "kind cannot be null");
        if (kind == PartyKind.UNKNOWN) {
            throw new IllegalArgumentException("Party kind must be PERSON or ORGANIZATION");
        }
    }
}
