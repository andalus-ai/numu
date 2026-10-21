package tech.kayys.numou.education.lms.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record AssignmentId(UUID value) implements DomainId<UUID> {
    public static AssignmentId of(UUID v) { return new AssignmentId(v); }
    public static AssignmentId generate() { return new AssignmentId(UUID.randomUUID()); }
}
