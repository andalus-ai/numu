package tech.kayys.numou.survey.domain.id;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import tech.kayys.numou.survey.domain.Guard;

public record SurveyId(String value) implements DomainId<String> {
    public SurveyId {
        value = Guard.notBlank(value, "surveyId");
    }

    public static SurveyId of(String value) {
        return new SurveyId(value);
    }

    @Override
    public String toString() { return value; }
}
