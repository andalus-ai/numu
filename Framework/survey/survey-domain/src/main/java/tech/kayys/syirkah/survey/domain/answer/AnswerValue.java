package tech.kayys.syirkah.survey.domain.answer;

import tech.kayys.syirkah.survey.domain.id.OptionId;

import java.time.LocalDate;
import java.util.*;

/** A respondent's answer. One variant per answer shape; question types validate the shape they accept. */
public sealed interface AnswerValue {

    record Text(String value) implements AnswerValue {
        public Text { value = value == null ? "" : value; }
    }

    record Numeric(double value) implements AnswerValue {}

    record Day(LocalDate value) implements AnswerValue {
        public Day { Objects.requireNonNull(value, "value"); }
    }

    record Bool(boolean value) implements AnswerValue {}

    /** Used for both single and multiple choice (single = exactly one selected). */
    record Choice(Set<OptionId> selected, String otherText) implements AnswerValue {
        public Choice {
            Set<OptionId> s = selected == null ? Set.of() : selected;
            selected = Set.copyOf(s);
            otherText = otherText == null ? "" : otherText;
        }

        public static Choice of(String... optionIds) {
            Set<OptionId> s = new LinkedHashSet<>();
            for (String id : optionIds) s.add(new OptionId(id));
            return new Choice(s, "");
        }

        public static Choice other(String text) { return new Choice(Set.of(), text); }
    }

    record Ranking(List<OptionId> order) implements AnswerValue {
        public Ranking { order = List.copyOf(order == null ? List.of() : order); }

        public static Ranking of(String... optionIds) {
            List<OptionId> l = new ArrayList<>();
            for (String id : optionIds) l.add(new OptionId(id));
            return new Ranking(l);
        }
    }

    /** row option id -> selected column option ids. */
    record Matrix(Map<OptionId, Set<OptionId>> cells) implements AnswerValue {
        public Matrix {
            Map<OptionId, Set<OptionId>> m = new LinkedHashMap<>();
            if (cells != null) cells.forEach((k, v) -> m.put(k, Set.copyOf(v)));
            cells = Collections.unmodifiableMap(m);
        }

        public static Matrix of(Map<String, List<String>> rows) {
            Map<OptionId, Set<OptionId>> m = new LinkedHashMap<>();
            rows.forEach((r, cols) -> {
                Set<OptionId> s = new LinkedHashSet<>();
                cols.forEach(c -> s.add(new OptionId(c)));
                m.put(new OptionId(r), s);
            });
            return new Matrix(m);
        }
    }

    /** "Empty" answers count as unanswered (blank text, nothing selected, ...). */
    static boolean isEmpty(AnswerValue a) {
        return switch (a) {
            case Text t -> t.value().isBlank();
            case Choice c -> c.selected().isEmpty() && c.otherText().isBlank();
            case Ranking r -> r.order().isEmpty();
            case Matrix m -> m.cells().values().stream().allMatch(Set::isEmpty);
            default -> false;
        };
    }
}
