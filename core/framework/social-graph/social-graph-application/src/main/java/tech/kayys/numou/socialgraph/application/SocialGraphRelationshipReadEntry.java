package tech.kayys.numou.socialgraph.application;

import tech.kayys.numou.ecosystem.domain.party.PartyId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Privacy-filtered relationship list item containing no profile PII. */
public record SocialGraphRelationshipReadEntry(UUID relationshipId, PartyId partyId, Instant followedAt) {
    public SocialGraphRelationshipReadEntry {
        Objects.requireNonNull(relationshipId, "relationshipId cannot be null");
        Objects.requireNonNull(partyId, "partyId cannot be null");
        Objects.requireNonNull(followedAt, "followedAt cannot be null");
    }
}
