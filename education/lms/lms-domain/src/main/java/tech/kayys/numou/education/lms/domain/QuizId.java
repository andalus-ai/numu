package tech.kayys.numou.education.lms.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record QuizId(UUID value) implements DomainId<UUID> {
    public static QuizId of(UUID v) { return new QuizId(v); }
    public static QuizId generate() { return new QuizId(UUID.randomUUID()); }
}
