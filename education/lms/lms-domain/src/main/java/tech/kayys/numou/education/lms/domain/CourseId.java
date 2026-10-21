package tech.kayys.numou.education.lms.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record CourseId(UUID value) implements DomainId<UUID> {
    public static CourseId of(UUID v) { return new CourseId(v); }
    public static CourseId generate() { return new CourseId(UUID.randomUUID()); }
}
