package tech.kayys.numou.survey.domain.id;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import tech.kayys.numou.survey.domain.Guard;

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
