package tech.kayys.syirkah.survey.domain.logic;

import tech.kayys.syirkah.survey.domain.answer.AnswerValue;
import tech.kayys.syirkah.survey.domain.id.OptionId;
import tech.kayys.syirkah.survey.domain.id.QuestionId;

import java.util.*;

/** Branching / skip-logic expression deciding whether a question is visible. */
public sealed interface Condition {

    Condition ALWAYS = new Always();

    boolean test(AnswerSheet answers);

    Set<QuestionId> references();

    record Always() implements Condition {
        public boolean test(AnswerSheet a) { return true; }
        public Set<QuestionId> references() { return Set.of(); }
    }

    record Answered(QuestionId question) implements Condition {
        public boolean test(AnswerSheet a) { return a.get(question).isPresent(); }
        public Set<QuestionId> references() { return Set.of(question); }
    }

    record ChoiceSelected(QuestionId question, OptionId option) implements Condition {
        public boolean test(AnswerSheet a) {
            return a.get(question).filter(v -> v instanceof AnswerValue.Choice c && c.selected().contains(option)).isPresent();
        }
        public Set<QuestionId> references() { return Set.of(question); }
    }

    record Compare(QuestionId question, CompareOp op, double value) implements Condition {
        public boolean test(AnswerSheet a) {
            return a.get(question).filter(v -> v instanceof AnswerValue.Numeric n && op.apply(n.value(), value)).isPresent();
        }
        public Set<QuestionId> references() { return Set.of(question); }
    }

    record BoolIs(QuestionId question, boolean expected) implements Condition {
        public boolean test(AnswerSheet a) {
            return a.get(question).filter(v -> v instanceof AnswerValue.Bool b && b.value() == expected).isPresent();
        }
        public Set<QuestionId> references() { return Set.of(question); }
    }

    record TextContains(QuestionId question, String needle) implements Condition {
        public boolean test(AnswerSheet a) {
            return a.get(question)
                    .filter(v -> v instanceof AnswerValue.Text t && t.value().toLowerCase().contains(needle.toLowerCase()))
                    .isPresent();
        }
        public Set<QuestionId> references() { return Set.of(question); }
    }

    record And(List<Condition> all) implements Condition {
        public And { all = List.copyOf(all); }
        public boolean test(AnswerSheet a) { return all.stream().allMatch(c -> c.test(a)); }
        public Set<QuestionId> references() { return union(all); }
    }

    record Or(List<Condition> any) implements Condition {
        public Or { any = List.copyOf(any); }
        public boolean test(AnswerSheet a) { return any.stream().anyMatch(c -> c.test(a)); }
        public Set<QuestionId> references() { return union(any); }
    }

    record Not(Condition inner) implements Condition {
        public boolean test(AnswerSheet a) { return !inner.test(a); }
        public Set<QuestionId> references() { return inner.references(); }
    }

    private static Set<QuestionId> union(List<Condition> cs) {
        Set<QuestionId> s = new LinkedHashSet<>();
        cs.forEach(c -> s.addAll(c.references()));
        return s;
    }
}
