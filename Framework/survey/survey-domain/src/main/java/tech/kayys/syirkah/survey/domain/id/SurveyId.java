package tech.kayys.syirkah.survey.domain.id;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import tech.kayys.syirkah.survey.domain.Guard;

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
