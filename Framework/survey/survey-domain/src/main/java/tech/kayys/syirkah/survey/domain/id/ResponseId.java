package tech.kayys.syirkah.survey.domain.id;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import tech.kayys.syirkah.survey.domain.Guard;

public record ResponseId(String value) implements DomainId<String> {
    public ResponseId {
        value = Guard.notBlank(value, "responseId");
    }

    public static ResponseId of(String value) {
        return new ResponseId(value);
    }

    @Override
    public String toString() { return value; }
}
