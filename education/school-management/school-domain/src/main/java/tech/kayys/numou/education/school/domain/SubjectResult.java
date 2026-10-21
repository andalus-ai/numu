package tech.kayys.numou.education.school.domain;

import tech.kayys.numou.education.core.Score;

import java.util.Objects;

/** One subject line on a report card: knowledge (pengetahuan) and skill (keterampilan) scores plus a description. */
public record SubjectResult(String subjectCode, Score knowledge, Score skill, String description) {
    public SubjectResult {
        if (subjectCode == null || subjectCode.isBlank()) throw new IllegalArgumentException("subjectCode must not be blank");
        Objects.requireNonNull(knowledge, "knowledge must not be null");
        Objects.requireNonNull(skill, "skill must not be null");
    }
}
