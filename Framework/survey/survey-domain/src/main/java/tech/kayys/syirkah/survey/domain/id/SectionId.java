package tech.kayys.syirkah.survey.domain.id;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import tech.kayys.syirkah.survey.domain.Guard;

public record SectionId(String value) implements DomainId<String> {
    public SectionId {
        value = Guard.notBlank(value, "sectionId");
    }

    public static SectionId of(String value) {
        return new SectionId(value);
    }

    @Override
    public String toString() { return value; }
}
