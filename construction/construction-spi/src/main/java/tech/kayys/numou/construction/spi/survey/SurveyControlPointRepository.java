package tech.kayys.numou.construction.spi.survey;

import tech.kayys.numou.construction.domain.survey.SurveyControlPoint;
import tech.kayys.numou.construction.domain.survey.SurveyControlPointId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface SurveyControlPointRepository extends Repository<SurveyControlPoint, SurveyControlPointId> {
    CompletionStage<List<SurveyControlPoint>> findBySiteId(UUID siteId);
}
