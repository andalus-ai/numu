package tech.kayys.syirkah.survey.domain.quiz;

import tech.kayys.syirkah.survey.domain.answer.AnswerValue;
import tech.kayys.syirkah.survey.domain.id.OptionId;
import tech.kayys.syirkah.survey.domain.question.Option;
import tech.kayys.syirkah.survey.domain.question.QuestionType;

import java.util.*;

/** Grading rule attached to a question (quiz mode). */
public sealed interface Scoring {

    Scoring NONE = new None();

    double maxPoints();

    /** Points earned for a (non-null) answer. Use {@link #earned(AnswerValue)} when the answer may be absent. */
    double score(AnswerValue answer);

    default double earned(AnswerValue answerOrNull) { return answerOrNull == null ? 0 : score(answerOrNull); }

    default boolean isScored() { return maxPoints() > 0; }

    default List<String> compatibilityProblems(QuestionType type) { return List.of(); }

    private static List<String> mismatch() { return List.of("SCORING_TYPE_MISMATCH"); }

    record None() implements Scoring {
        public double maxPoints() { return 0; }
        public double score(AnswerValue a) { return 0; }
    }

    /** All-or-nothing: the selected set must equal the correct set. Works for single and multi choice. */
    record ExactChoice(Set<OptionId> correct, double points) implements Scoring {
        public ExactChoice { correct = Set.copyOf(correct); }

        public double maxPoints() { return points; }

        public double score(AnswerValue a) {
            return a instanceof AnswerValue.Choice c && c.otherText().isBlank() && c.selected().equals(correct) ? points : 0;
        }

        @Override
        public List<String> compatibilityProblems(QuestionType t) {
            if (!(t instanceof QuestionType.SingleChoice || t instanceof QuestionType.MultiChoice)) return Scoring.mismatch();
            if (correct.isEmpty()) return List.of("SCORING_NO_CORRECT_OPTION");
            return t.optionIds().containsAll(correct) ? List.of() : List.of("SCORING_UNKNOWN_OPTION");
        }
    }

    /** Partial credit for multi choice: proportion of correct options picked, optionally minus wrong picks. */
    record PartialChoices(Set<OptionId> correct, double points, boolean penalizeWrong) implements Scoring {
        public PartialChoices { correct = Set.copyOf(correct); }

        public double maxPoints() { return points; }

        public double score(AnswerValue a) {
            if (!(a instanceof AnswerValue.Choice c) || correct.isEmpty()) return 0;
            long right = c.selected().stream().filter(correct::contains).count();
            long wrong = c.selected().size() - right;
            double net = right - (penalizeWrong ? wrong : 0);
            return points * Math.max(0, net) / correct.size();
        }

        @Override
        public List<String> compatibilityProblems(QuestionType t) {
            if (!(t instanceof QuestionType.MultiChoice)) return Scoring.mismatch();
            if (correct.isEmpty()) return List.of("SCORING_NO_CORRECT_OPTION");
            return t.optionIds().containsAll(correct) ? List.of() : List.of("SCORING_UNKNOWN_OPTION");
        }
    }

    record TextMatch(Set<String> accepted, boolean ignoreCase, double points) implements Scoring {
        public TextMatch { accepted = Set.copyOf(accepted); }

        public double maxPoints() { return points; }

        public double score(AnswerValue a) {
            if (!(a instanceof AnswerValue.Text t)) return 0;
            String given = t.value().trim();
            return accepted.stream().anyMatch(x -> ignoreCase ? x.trim().equalsIgnoreCase(given) : x.trim().equals(given))
                    ? points : 0;
        }

        @Override
        public List<String> compatibilityProblems(QuestionType t) {
            if (!(t instanceof QuestionType.ShortText || t instanceof QuestionType.LongText)) return Scoring.mismatch();
            return accepted.isEmpty() ? List.of("SCORING_NO_ACCEPTED_TEXT") : List.of();
        }
    }

    record NumericMatch(double expected, double tolerance, double points) implements Scoring {
        public double maxPoints() { return points; }

        public double score(AnswerValue a) {
            return a instanceof AnswerValue.Numeric n && Math.abs(n.value() - expected) <= tolerance ? points : 0;
        }

        @Override
        public List<String> compatibilityProblems(QuestionType t) {
            return (t instanceof QuestionType.NumberInput || t instanceof QuestionType.Rating || t instanceof QuestionType.Scale)
                    ? List.of() : Scoring.mismatch();
        }
    }

    record BoolMatch(boolean expected, double points) implements Scoring {
        public double maxPoints() { return points; }

        public double score(AnswerValue a) {
            return a instanceof AnswerValue.Bool b && b.value() == expected ? points : 0;
        }

        @Override
        public List<String> compatibilityProblems(QuestionType t) {
            return t instanceof QuestionType.YesNo ? List.of() : Scoring.mismatch();
        }
    }

    record OrderMatch(List<OptionId> correctOrder, double points) implements Scoring {
        public OrderMatch { correctOrder = List.copyOf(correctOrder); }

        public double maxPoints() { return points; }

        public double score(AnswerValue a) {
            return a instanceof AnswerValue.Ranking r && r.order().equals(correctOrder) ? points : 0;
        }

        @Override
        public List<String> compatibilityProblems(QuestionType t) {
            if (!(t instanceof QuestionType.Ranking)) return Scoring.mismatch();
            return t.optionIds().containsAll(correctOrder) ? List.of() : List.of("SCORING_UNKNOWN_OPTION");
        }
    }

    /** Per-row credit: points * (rows answered correctly / expected rows). */
    record MatrixMatch(Map<OptionId, Set<OptionId>> expected, double points) implements Scoring {
        public MatrixMatch {
            Map<OptionId, Set<OptionId>> m = new LinkedHashMap<>();
            expected.forEach((k, v) -> m.put(k, Set.copyOf(v)));
            expected = Collections.unmodifiableMap(m);
        }

        public double maxPoints() { return points; }

        public double score(AnswerValue a) {
            if (!(a instanceof AnswerValue.Matrix m) || expected.isEmpty()) return 0;
            long ok = expected.entrySet().stream()
                    .filter(e -> e.getValue().equals(m.cells().getOrDefault(e.getKey(), Set.of()))).count();
            return points * ok / expected.size();
        }

        @Override
        public List<String> compatibilityProblems(QuestionType t) {
            if (!(t instanceof QuestionType.Matrix mx)) return Scoring.mismatch();
            Set<OptionId> rows = new HashSet<>(), cols = new HashSet<>();
            mx.rows().forEach(o -> rows.add(o.id()));
            mx.columns().forEach(o -> cols.add(o.id()));
            for (var e : expected.entrySet()) {
                if (!rows.contains(e.getKey()) || !cols.containsAll(e.getValue())) return List.of("SCORING_UNKNOWN_OPTION");
            }
            return List.of();
        }
    }
}
