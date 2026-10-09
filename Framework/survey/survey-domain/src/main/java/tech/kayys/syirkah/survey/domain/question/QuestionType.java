package tech.kayys.syirkah.survey.domain.question;

import tech.kayys.syirkah.survey.domain.answer.AnswerValue;
import tech.kayys.syirkah.survey.domain.id.OptionId;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The "format" of a question. Each variant owns its configuration and knows how to validate an answer
 * of the matching shape. Add a new format = add a record here (the compiler then points at every
 * exhaustive switch that needs updating: aggregation, JSON codec, ...).
 */
public sealed interface QuestionType {

    String kind();

    /** @return violation codes; empty when the answer is acceptable. */
    List<String> validate(AnswerValue answer);

    default List<String> definitionProblems() { return List.of(); }

    default List<Option> options() { return List.of(); }

    default Set<OptionId> optionIds() { return Checks.ids(options()); }

    // ---------------------------------------------------------------- simple formats

    record ShortText(int maxLength, String pattern) implements QuestionType {
        public ShortText { if (maxLength <= 0) maxLength = 255; }

        public String kind() { return "short_text"; }

        public List<String> validate(AnswerValue a) {
            if (!(a instanceof AnswerValue.Text t)) return Checks.mismatch(kind());
            List<String> p = new ArrayList<>();
            if (t.value().length() > maxLength) p.add("TOO_LONG");
            if (pattern != null && !pattern.isBlank() && !Pattern.matches(pattern, t.value())) p.add("PATTERN_MISMATCH");
            return p;
        }

        @Override
        public List<String> definitionProblems() {
            if (pattern == null || pattern.isBlank()) return List.of();
            try { Pattern.compile(pattern); } catch (PatternSyntaxException e) { return List.of("INVALID_PATTERN"); }
            return List.of();
        }
    }

    record LongText(int maxLength) implements QuestionType {
        public LongText { if (maxLength <= 0) maxLength = 10_000; }

        public String kind() { return "long_text"; }

        public List<String> validate(AnswerValue a) {
            if (!(a instanceof AnswerValue.Text t)) return Checks.mismatch(kind());
            return t.value().length() > maxLength ? List.of("TOO_LONG") : List.of();
        }
    }

    record NumberInput(Double min, Double max, boolean integerOnly) implements QuestionType {
        public String kind() { return "number"; }

        public List<String> validate(AnswerValue a) {
            if (!(a instanceof AnswerValue.Numeric n)) return Checks.mismatch(kind());
            List<String> p = new ArrayList<>();
            if (integerOnly && n.value() != Math.rint(n.value())) p.add("NOT_AN_INTEGER");
            if (min != null && n.value() < min) p.add("BELOW_MIN");
            if (max != null && n.value() > max) p.add("ABOVE_MAX");
            return p;
        }

        @Override
        public List<String> definitionProblems() {
            return (min != null && max != null && min > max) ? List.of("MIN_GREATER_THAN_MAX") : List.of();
        }
    }

    record DateInput(LocalDate min, LocalDate max) implements QuestionType {
        public String kind() { return "date"; }

        public List<String> validate(AnswerValue a) {
            if (!(a instanceof AnswerValue.Day d)) return Checks.mismatch(kind());
            List<String> p = new ArrayList<>();
            if (min != null && d.value().isBefore(min)) p.add("BEFORE_MIN");
            if (max != null && d.value().isAfter(max)) p.add("AFTER_MAX");
            return p;
        }
    }

    record YesNo() implements QuestionType {
        public String kind() { return "yes_no"; }

        public List<String> validate(AnswerValue a) {
            return a instanceof AnswerValue.Bool ? List.of() : Checks.mismatch(kind());
        }
    }

    // ---------------------------------------------------------------- choice formats

    record SingleChoice(List<Option> options, boolean allowOther) implements QuestionType {
        public SingleChoice { options = List.copyOf(options); }

        public String kind() { return "single_choice"; }

        public List<String> validate(AnswerValue a) {
            return Checks.choice(a, optionIds(), allowOther, 1, 1, kind());
        }

        @Override
        public List<String> definitionProblems() { return Checks.optionProblems(options, "OPTIONS"); }
    }

    record MultiChoice(List<Option> options, int minSelections, Integer maxSelections, boolean allowOther)
            implements QuestionType {
        public MultiChoice {
            options = List.copyOf(options);
            minSelections = Math.max(0, minSelections);
        }

        public String kind() { return "multi_choice"; }

        public List<String> validate(AnswerValue a) {
            return Checks.choice(a, optionIds(), allowOther, minSelections,
                    maxSelections == null ? Integer.MAX_VALUE : maxSelections, kind());
        }

        @Override
        public List<String> definitionProblems() {
            List<String> p = new ArrayList<>(Checks.optionProblems(options, "OPTIONS"));
            if (maxSelections != null && maxSelections < minSelections) p.add("MAX_LESS_THAN_MIN");
            if (minSelections > options.size()) p.add("MIN_EXCEEDS_OPTION_COUNT");
            return p;
        }
    }

    // ---------------------------------------------------------------- scale formats

    /** Star-style rating 1..max. */
    record Rating(int max) implements QuestionType {
        public String kind() { return "rating"; }

        public List<String> validate(AnswerValue a) { return Checks.integerInRange(a, 1, max, kind()); }

        @Override
        public List<String> definitionProblems() { return (max < 2 || max > 10) ? List.of("MAX_MUST_BE_2_TO_10") : List.of(); }
    }

    /** Linear scale; NPS is Scale(0, 10). */
    record Scale(int min, int max, String minLabel, String maxLabel) implements QuestionType {
        public Scale {
            minLabel = minLabel == null ? "" : minLabel;
            maxLabel = maxLabel == null ? "" : maxLabel;
        }

        public String kind() { return "scale"; }

        public List<String> validate(AnswerValue a) { return Checks.integerInRange(a, min, max, kind()); }

        @Override
        public List<String> definitionProblems() { return min >= max ? List.of("MIN_MUST_BE_LESS_THAN_MAX") : List.of(); }
    }

    // ---------------------------------------------------------------- complex formats

    /** Order the options by preference; optionally only the top N. */
    record Ranking(List<Option> options, Integer topN) implements QuestionType {
        public Ranking { options = List.copyOf(options); }

        public String kind() { return "ranking"; }

        public List<String> validate(AnswerValue a) {
            if (!(a instanceof AnswerValue.Ranking r)) return Checks.mismatch(kind());
            List<String> p = new ArrayList<>();
            Set<OptionId> valid = optionIds();
            if (new HashSet<>(r.order()).size() != r.order().size()) p.add("DUPLICATE_RANK");
            for (OptionId id : r.order()) if (!valid.contains(id)) p.add("UNKNOWN_OPTION:" + id);
            int expected = topN == null ? options.size() : Math.min(topN, options.size());
            if (r.order().size() != expected) p.add("WRONG_RANK_COUNT");
            return p;
        }

        @Override
        public List<String> definitionProblems() {
            List<String> p = new ArrayList<>(Checks.optionProblems(options, "OPTIONS"));
            if (topN != null && topN < 1) p.add("TOP_N_MUST_BE_POSITIVE");
            return p;
        }
    }

    /** Grid question: rows x columns, one or many columns per row. */
    record Matrix(List<Option> rows, List<Option> columns, boolean multiplePerRow, boolean requireAllRows)
            implements QuestionType {
        public Matrix {
            rows = List.copyOf(rows);
            columns = List.copyOf(columns);
        }

        public String kind() { return "matrix"; }

        public List<String> validate(AnswerValue a) {
            if (!(a instanceof AnswerValue.Matrix m)) return Checks.mismatch(kind());
            List<String> p = new ArrayList<>();
            Set<OptionId> rowIds = Checks.ids(rows), colIds = Checks.ids(columns);
            m.cells().forEach((row, cols) -> {
                if (!rowIds.contains(row)) p.add("UNKNOWN_ROW:" + row);
                for (OptionId c : cols) if (!colIds.contains(c)) p.add("UNKNOWN_COLUMN:" + c);
                if (!multiplePerRow && cols.size() > 1) p.add("TOO_MANY_IN_ROW:" + row);
            });
            if (requireAllRows) {
                for (Option r : rows) {
                    if (m.cells().getOrDefault(r.id(), Set.of()).isEmpty()) p.add("MISSING_ROW:" + r.id());
                }
            }
            return p;
        }

        @Override
        public List<String> definitionProblems() {
            List<String> p = new ArrayList<>(Checks.optionProblems(rows, "ROWS"));
            p.addAll(Checks.optionProblems(columns, "COLUMNS"));
            return p;
        }
    }
}
