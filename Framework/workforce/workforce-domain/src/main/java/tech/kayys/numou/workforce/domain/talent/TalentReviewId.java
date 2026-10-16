package tech.kayys.numou.workforce.domain.talent;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record TalentReviewId(UUID value) implements DomainId<UUID> {
    public TalentReviewId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static TalentReviewId generate() {
        return new TalentReviewId(UUID.randomUUID());
    }

    public static TalentReviewId of(UUID value) {
        return new TalentReviewId(value);
    }
}
