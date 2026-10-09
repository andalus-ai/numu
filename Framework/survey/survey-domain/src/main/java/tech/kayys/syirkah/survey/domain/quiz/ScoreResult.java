package tech.kayys.syirkah.survey.domain.quiz;

import java.util.List;

public record ScoreResult(double earned, double max, double percent, boolean passed, List<QuestionScore> items) {
    public ScoreResult { items = List.copyOf(items); }

    /** Same totals, without the per-question breakdown (used when correct answers must stay hidden). */
    public ScoreResult withoutDetails() { return new ScoreResult(earned, max, percent, passed, List.of()); }
}
