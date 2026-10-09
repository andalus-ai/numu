package tech.kayys.syirkah.survey.domain.question;

import tech.kayys.syirkah.survey.domain.Guard;
import tech.kayys.syirkah.survey.domain.id.OptionId;

public record Option(OptionId id, String label) {
    public Option {
        Guard.notNull(id, "option id");
        label = Guard.notBlank(label, "option label");
    }

    public static Option of(String id, String label) { return new Option(new OptionId(id), label); }
}
