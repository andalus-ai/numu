package tech.kayys.syirkah.construction.domain.survey;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record SurveyControlPointId(UUID value) implements DomainId<UUID> {
    public SurveyControlPointId { Objects.requireNonNull(value); }
    public static SurveyControlPointId generate() { return new SurveyControlPointId(UUID.randomUUID()); }
    public static SurveyControlPointId of(UUID value) { return new SurveyControlPointId(value); }
}
