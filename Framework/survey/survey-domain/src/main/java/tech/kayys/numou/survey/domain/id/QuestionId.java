package tech.kayys.numou.survey.domain.id;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import tech.kayys.numou.survey.domain.Guard;

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
