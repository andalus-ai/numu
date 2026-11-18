package tech.kayys.numou.feedback.runtime.quarkus;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.feedback.adapter.postgres.PostgresFeedbackSurveyRepository;
import tech.kayys.numou.feedback.adapter.postgres.PostgresPublicInvitationRateLimitRepository;
import tech.kayys.numou.feedback.adapter.survey.SurveyResponseSubmissionBridge;
import tech.kayys.numou.feedback.application.command.SubmitDistributedSurveyResponseHandler;
import tech.kayys.numou.feedback.application.port.DistributedSurveyResponsePort;
import tech.kayys.numou.feedback.application.port.PublicInvitationRateLimitRepository;
import tech.kayys.numou.feedback.application.port.PublicSurveyAccessPort;
import tech.kayys.numou.feedback.application.port.SurveyDistributionRepository;
import tech.kayys.numou.feedback.application.query.ResolveSurveyDistributionHandler;
import tech.kayys.numou.feedback.application.service.PublicInvitationRateLimiter;
import tech.kayys.numou.feedback.application.service.PublicSurveyInvitationService;
import tech.kayys.numou.survey.application.port.in.ResponseUseCase;
import tech.kayys.numou.survey.application.port.in.SurveyAuthoringUseCase;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Optional composition root. The host must provide Survey's application ports;
 * this module supplies Feedback's PostgreSQL and Survey bridge adapters.
 */
@ApplicationScoped
public class FeedbackPublicInvitationWiring {
    @Produces
    @ApplicationScoped
    PostgresFeedbackSurveyRepository feedbackSurveyRepository(DataSource dataSource) {
        return new PostgresFeedbackSurveyRepository(dataSource);
    }

    @Produces
    @ApplicationScoped
    PostgresPublicInvitationRateLimitRepository publicInvitationRateLimitRepository(DataSource dataSource) {
        return new PostgresPublicInvitationRateLimitRepository(dataSource);
    }

    @Produces
    @ApplicationScoped
    SurveyResponseSubmissionBridge surveyResponseSubmissionBridge(
            ResponseUseCase responses, SurveyAuthoringUseCase surveys, Clock clock) {
        return new SurveyResponseSubmissionBridge(responses, surveys, clock);
    }

    @Produces
    @ApplicationScoped
    ResolveSurveyDistributionHandler resolveSurveyDistributionHandler(
            SurveyDistributionRepository distributions) {
        return new ResolveSurveyDistributionHandler(distributions);
    }

    @Produces
    @ApplicationScoped
    SubmitDistributedSurveyResponseHandler submitDistributedSurveyResponseHandler(
            ResolveSurveyDistributionHandler resolver, SurveyDistributionRepository distributions,
            DistributedSurveyResponsePort surveyResponses) {
        return new SubmitDistributedSurveyResponseHandler(resolver, distributions, surveyResponses);
    }

    @Produces
    @ApplicationScoped
    PublicSurveyInvitationService publicSurveyInvitationService(
            ResolveSurveyDistributionHandler resolver, PublicSurveyAccessPort survey,
            SubmitDistributedSurveyResponseHandler submissions) {
        return new PublicSurveyInvitationService(resolver, survey, submissions);
    }

    @Produces
    @ApplicationScoped
    PublicInvitationRateLimiter publicInvitationRateLimiter(
            PublicInvitationRateLimitRepository repository,
            @ConfigProperty(name = "numou.feedback.public-invitations.requests-per-minute", defaultValue = "60")
            int requestsPerMinute) {
        return new PublicInvitationRateLimiter(repository, requestsPerMinute);
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    Clock feedbackClock() {
        return Clock.systemUTC();
    }
}