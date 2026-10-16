package tech.kayys.numou.survey.domain.question;

import tech.kayys.numou.survey.domain.Guard;
import tech.kayys.numou.survey.domain.id.OptionId;

public record Option(OptionId id, String label) {
    public Option {
        Guard.notNull(id, "option id");
        label = Guard.notBlank(label, "option label");
    }

    public static Option of(String id, String label) { return new Option(new OptionId(id), label); }
}
