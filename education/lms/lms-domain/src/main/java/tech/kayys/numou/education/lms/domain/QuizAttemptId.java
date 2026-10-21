package tech.kayys.numou.education.lms.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record QuizAttemptId(UUID value) implements DomainId<UUID> {
    public static QuizAttemptId of(UUID v) { return new QuizAttemptId(v); }
    public static QuizAttemptId generate() { return new QuizAttemptId(UUID.randomUUID()); }
}
