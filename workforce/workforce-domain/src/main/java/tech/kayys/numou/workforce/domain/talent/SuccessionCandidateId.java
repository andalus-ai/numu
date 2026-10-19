package tech.kayys.numou.workforce.domain.talent;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record SuccessionCandidateId(UUID value) implements DomainId<UUID> {
    public SuccessionCandidateId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static SuccessionCandidateId generate() {
        return new SuccessionCandidateId(UUID.randomUUID());
    }

    public static SuccessionCandidateId of(UUID value) {
        return new SuccessionCandidateId(value);
    }
}
