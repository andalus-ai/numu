package tech.kayys.numou.education.lms.domain;

import java.util.List;

/** A multiple-choice question. {@code correctIndex} is the zero-based index into {@code options}. */
public record QuizQuestion(String text, List<String> options, int correctIndex, int points) {
    public QuizQuestion {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Question text must not be blank");
        if (options == null || options.size() < 2) throw new IllegalArgumentException("A question needs at least two options");
        options = List.copyOf(options);
        if (correctIndex < 0 || correctIndex >= options.size()) throw new IllegalArgumentException("correctIndex is out of range");
        if (points <= 0) throw new IllegalArgumentException("points must be positive");
    }
}
