package tech.kayys.numou.workforce.domain.talent;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record TalentSegmentId(UUID value) implements DomainId<UUID> {
    public TalentSegmentId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static TalentSegmentId generate() {
        return new TalentSegmentId(UUID.randomUUID());
    }

    public static TalentSegmentId of(UUID value) {
        return new TalentSegmentId(value);
    }
}
