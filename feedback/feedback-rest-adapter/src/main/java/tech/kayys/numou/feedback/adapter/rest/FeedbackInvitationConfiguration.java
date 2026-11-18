package tech.kayys.numou.feedback.adapter.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.feedback.application.command.SubmitDistributedSurveyResponseHandler;
import tech.kayys.numou.feedback.application.port.DistributedSurveyResponsePort;
import tech.kayys.numou.feedback.application.port.SurveyDistributionRepository;
import tech.kayys.numou.feedback.application.query.ResolveSurveyDistributionHandler;
import tech.kayys.numou.feedback.adapter.survey.SurveyResponseSubmissionBridge;
import tech.kayys.numou.survey.application.port.in.ResponseUseCase;

/** CDI composition for the inbound REST adapter; persistence remains supplied by the host runtime. */
@ApplicationScoped
public class FeedbackInvitationConfiguration {
    @Produces
    @ApplicationScoped
    public ResolveSurveyDistributionHandler resolveSurveyDistributionHandler(SurveyDistributionRepository repository) {
        return new ResolveSurveyDistributionHandler(repository);
    }

    @Produces
    @ApplicationScoped
    public DistributedSurveyResponsePort distributedSurveyResponsePort(ResponseUseCase responses) {
        return new SurveyResponseSubmissionBridge(responses);
    }

    @Produces
    @ApplicationScoped
    public SubmitDistributedSurveyResponseHandler submitDistributedSurveyResponseHandler(
            ResolveSurveyDistributionHandler resolver, SurveyDistributionRepository repository,
            DistributedSurveyResponsePort surveyResponses) {
        return new SubmitDistributedSurveyResponseHandler(resolver, repository, surveyResponses);
    }
}
