package tech.kayys.syirkah.survey.domain.id;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import tech.kayys.syirkah.survey.domain.Guard;

public record OptionId(String value) implements DomainId<String> {
    public OptionId {
        value = Guard.notBlank(value, "optionId");
    }

    public static OptionId of(String value) {
        return new OptionId(value);
    }

    @Override
    public String toString() { return value; }
}
