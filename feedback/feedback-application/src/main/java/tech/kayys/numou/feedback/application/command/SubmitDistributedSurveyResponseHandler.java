package tech.kayys.numou.feedback.application.command;

import tech.kayys.numou.feedback.application.port.DistributedSurveyResponsePort;
import tech.kayys.numou.feedback.application.port.SurveyDistributionRepository;
import tech.kayys.numou.feedback.application.query.ResolveSurveyDistributionHandler;
import tech.kayys.numou.feedback.domain.survey.SurveyDistribution;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/**
 * Coordinates a public response submission with a durable invitation quota reservation.
 * A reservation is taken before Survey submission, committed after success, and released
 * on a synchronous failure. An operational reconciler must recover abandoned reservations.
 */
public final class SubmitDistributedSurveyResponseHandler {
    private final ResolveSurveyDistributionHandler resolver;
    private final SurveyDistributionRepository distributions;
    private final DistributedSurveyResponsePort surveyResponses;

    public SubmitDistributedSurveyResponseHandler(ResolveSurveyDistributionHandler resolver,
            SurveyDistributionRepository distributions, DistributedSurveyResponsePort surveyResponses) {
        this.resolver = Objects.requireNonNull(resolver, "resolver cannot be null");
        this.distributions = Objects.requireNonNull(distributions, "distributions cannot be null");
        this.surveyResponses = Objects.requireNonNull(surveyResponses, "surveyResponses cannot be null");
    }

    public Outcome handle(String rawToken, String responseId, Instant now) {
        if (responseId == null || responseId.isBlank() || responseId.length() > 200)
            throw new IllegalArgumentException("responseId must contain 1..200 characters");
        SurveyDistribution distribution = resolver.handle(rawToken)
                .orElseThrow(InvalidSurveyInvitationException::new);
        TenantId tenantId = distribution.tenantId();
        String id = responseId.trim();
        SurveyDistributionRepository.ReservationResult reservation =
                distributions.reserveResponse(tenantId, distribution.id(), id, now);

        if (reservation == SurveyDistributionRepository.ReservationResult.ALREADY_COMPLETED)
            return Outcome.ALREADY_SUBMITTED;
        if (reservation == SurveyDistributionRepository.ReservationResult.ALREADY_RESERVED)
            return Outcome.SUBMISSION_IN_PROGRESS;
        if (reservation == SurveyDistributionRepository.ReservationResult.NOT_ACTIVE)
            throw new InvalidSurveyInvitationException();
        if (reservation == SurveyDistributionRepository.ReservationResult.LIMIT_REACHED)
            throw new SurveyDistributionLimitReachedException();

        try {
            surveyResponses.submit(tenantId, distribution.surveyId(), id);
            distributions.completeResponse(tenantId, distribution.id(), id, now);
            return Outcome.SUBMITTED;
        } catch (RuntimeException failure) {
            try {
                distributions.releaseResponse(tenantId, distribution.id(), id, now);
            } catch (RuntimeException releaseFailure) {
                failure.addSuppressed(releaseFailure);
            }
            throw failure;
        }
    }

    public enum Outcome { SUBMITTED, ALREADY_SUBMITTED, SUBMISSION_IN_PROGRESS }

    public static final class InvalidSurveyInvitationException extends RuntimeException {
        public InvalidSurveyInvitationException() { super("Survey invitation is invalid or expired"); }
    }

    public static final class SurveyDistributionLimitReachedException extends RuntimeException {
        public SurveyDistributionLimitReachedException() { super("Survey invitation response limit has been reached"); }
    }
}
