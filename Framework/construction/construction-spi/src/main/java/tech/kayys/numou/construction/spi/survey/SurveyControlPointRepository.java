package tech.kayys.syirkah.construction.spi.survey;

import tech.kayys.syirkah.construction.domain.survey.SurveyControlPoint;
import tech.kayys.syirkah.construction.domain.survey.SurveyControlPointId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface SurveyControlPointRepository extends Repository<SurveyControlPoint, SurveyControlPointId> {
    CompletionStage<List<SurveyControlPoint>> findBySiteId(UUID siteId);
}
