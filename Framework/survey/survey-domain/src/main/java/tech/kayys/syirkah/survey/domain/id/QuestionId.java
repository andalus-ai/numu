package tech.kayys.syirkah.survey.domain.id;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import tech.kayys.syirkah.survey.domain.Guard;

public record QuestionId(String value) implements DomainId<String> {
    public QuestionId {
        value = Guard.notBlank(value, "questionId");
    }

    public static QuestionId of(String value) {
        return new QuestionId(value);
    }

    @Override
    public String toString() { return value; }
}
