package tech.kayys.numou.workforce.domain.learning;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record LearningCertificateId(UUID value) implements DomainId<UUID> {
    public LearningCertificateId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static LearningCertificateId generate() {
        return new LearningCertificateId(UUID.randomUUID());
    }

    public static LearningCertificateId of(UUID value) {
        return new LearningCertificateId(value);
    }
}
