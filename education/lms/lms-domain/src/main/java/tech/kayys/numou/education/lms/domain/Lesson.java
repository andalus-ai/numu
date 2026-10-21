package tech.kayys.numou.education.lms.domain;

import java.util.UUID;

/** One unit of course content. {@code contentRef} points to the document/storage capability, never raw binary. */
public record Lesson(UUID lessonId, String title, LessonType type, String contentRef, int position) {
    public Lesson {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Lesson title must not be blank");
        if (type == null) throw new IllegalArgumentException("Lesson type must not be null");
    }
}
