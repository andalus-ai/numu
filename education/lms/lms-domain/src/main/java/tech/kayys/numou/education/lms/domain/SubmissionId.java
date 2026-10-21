package tech.kayys.numou.education.lms.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record SubmissionId(UUID value) implements DomainId<UUID> {
    public static SubmissionId of(UUID v) { return new SubmissionId(v); }
    public static SubmissionId generate() { return new SubmissionId(UUID.randomUUID()); }
}
