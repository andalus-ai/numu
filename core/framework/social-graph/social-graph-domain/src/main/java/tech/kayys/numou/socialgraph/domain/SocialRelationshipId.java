package tech.kayys.numou.socialgraph.domain;

import java.util.Objects;
import java.util.UUID;

public record SocialRelationshipId(UUID value) {
    public SocialRelationshipId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static SocialRelationshipId generate() {
        return new SocialRelationshipId(UUID.randomUUID());
    }

    public static SocialRelationshipId of(UUID value) {
        return new SocialRelationshipId(value);
    }
}
