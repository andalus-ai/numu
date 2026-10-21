package tech.kayys.numou.education.school.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record LessonSlotId(UUID value) implements DomainId<UUID> {
    public static LessonSlotId of(UUID v) { return new LessonSlotId(v); }
    public static LessonSlotId generate() { return new LessonSlotId(UUID.randomUUID()); }
}
