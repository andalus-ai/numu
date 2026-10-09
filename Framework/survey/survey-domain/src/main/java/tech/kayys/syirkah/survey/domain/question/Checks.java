package tech.kayys.syirkah.survey.domain.question;

import tech.kayys.syirkah.survey.domain.answer.AnswerValue;
import tech.kayys.syirkah.survey.domain.id.OptionId;

import java.util.*;

/** Shared validation helpers for question types. */
final class Checks {
    private Checks() {}

    static List<String> mismatch(String kind) { return List.of("TYPE_MISMATCH:" + kind); }

    static Set<OptionId> ids(List<Option> options) {
        Set<OptionId> s = new LinkedHashSet<>();
        options.forEach(o -> s.add(o.id()));
        return s;
    }

    static List<String> integerInRange(AnswerValue a, int lo, int hi, String kind) {
        if (!(a instanceof AnswerValue.Numeric n)) return mismatch(kind);
        List<String> p = new ArrayList<>();
        if (n.value() != Math.rint(n.value())) p.add("NOT_AN_INTEGER");
        if (n.value() < lo || n.value() > hi) p.add("OUT_OF_RANGE");
        return p;
    }

    static List<String> choice(AnswerValue a, Set<OptionId> valid, boolean allowOther, int min, int max, String kind) {
        if (!(a instanceof AnswerValue.Choice c)) return mismatch(kind);
        List<String> p = new ArrayList<>();
        for (OptionId id : c.selected()) if (!valid.contains(id)) p.add("UNKNOWN_OPTION:" + id);
        boolean hasOther = !c.otherText().isBlank();
        if (hasOther && !allowOther) p.add("OTHER_NOT_ALLOWED");
        int count = c.selected().size() + (hasOther ? 1 : 0);
        if (count < min) p.add("TOO_FEW_SELECTIONS");
        if (count > max) p.add("TOO_MANY_SELECTIONS");
        return p;
    }

    static List<String> optionProblems(List<Option> options, String label) {
        List<String> p = new ArrayList<>();
        if (options.isEmpty()) p.add(label + "_EMPTY");
        if (ids(options).size() != options.size()) p.add(label + "_DUPLICATE_ID");
        return p;
    }
}
