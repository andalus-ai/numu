package tech.kayys.numou.workforce.domain.talent;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record TalentReviewCycleId(UUID value) implements DomainId<UUID> {
    public TalentReviewCycleId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static TalentReviewCycleId generate() {
        return new TalentReviewCycleId(UUID.randomUUID());
    }

    public static TalentReviewCycleId of(UUID value) {
        return new TalentReviewCycleId(value);
    }
}
