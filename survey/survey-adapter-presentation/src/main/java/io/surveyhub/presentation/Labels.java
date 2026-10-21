package io.surveyhub.presentation;

import io.surveyhub.domain.id.OptionId;
import io.surveyhub.domain.question.Option;
import io.surveyhub.domain.question.Question;
import io.surveyhub.domain.question.QuestionType;

import java.util.HashMap;
import java.util.Map;

/** Option id -> human label lookup for one question (rows and columns of matrices included). */
final class Labels {
    private Labels() {}

    static Map<String, String> of(Question q) {
        Map<String, String> m = new HashMap<>();
        for (Option o : q.type().options()) m.put(o.id().value(), o.label());
        if (q.type() instanceof QuestionType.Matrix mx) {
            for (Option o : mx.rows()) m.put(o.id().value(), o.label());
            for (Option o : mx.columns()) m.put(o.id().value(), o.label());
        }
        return m;
    }

    static String label(Map<String, String> m, OptionId id) { return m.getOrDefault(id.value(), id.value()); }
}
