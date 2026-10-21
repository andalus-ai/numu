package tech.kayys.numou.education.core;

import java.io.Serializable;
import java.util.Objects;

/** Tingkat: an education level plus a grade number valid for that level. */
public record GradeLevel(EducationLevel level, int grade) implements Serializable {
    public GradeLevel {
        Objects.requireNonNull(level, "level must not be null");
        if (!level.acceptsGrade(grade)) {
            throw new IllegalArgumentException(
                    "Grade " + grade + " is not valid for " + level
                            + " (" + level.minGrade() + "-" + level.maxGrade() + ")");
        }
    }

    public static GradeLevel of(EducationLevel level, int grade) { return new GradeLevel(level, grade); }

    @Override
    public String toString() { return level + " " + grade; }
}
