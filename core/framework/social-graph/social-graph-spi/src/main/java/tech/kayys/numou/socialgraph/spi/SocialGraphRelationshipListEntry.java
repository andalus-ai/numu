package tech.kayys.numou.socialgraph.spi;

import tech.kayys.numou.ecosystem.domain.party.PartyId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Minimal relationship-list projection; never embeds profile or contact data. */
public record SocialGraphRelationshipListEntry(UUID relationshipId, PartyId partyId, Instant createdAt) {
    public SocialGraphRelationshipListEntry {
        Objects.requireNonNull(relationshipId, "relationshipId cannot be null");
        Objects.requireNonNull(partyId, "partyId cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
    }
}
