package tech.kayys.syirkah.survey.domain.event;

import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.survey.domain.id.ResponseId;
import tech.kayys.syirkah.survey.domain.id.SurveyId;

import java.time.Instant;
import java.util.UUID;

public sealed interface DomainEvent extends tech.kayys.syirkah.foundation.domain.event.DomainEvent {
    TenantId tenantId();

    record SurveyPublished(UUID eventId, TenantId tenantId, SurveyId surveyId, int version, Instant occurredAt) implements DomainEvent {
        public SurveyPublished(TenantId tenantId, SurveyId surveyId, int version, Instant occurredAt) {
            this(UUID.randomUUID(), tenantId, surveyId, version, occurredAt);
        }

        @Override
        public String eventType() {
            return "survey.published";
        }
    }

    record SurveyClosed(UUID eventId, TenantId tenantId, SurveyId surveyId, Instant occurredAt) implements DomainEvent {
        public SurveyClosed(TenantId tenantId, SurveyId surveyId, Instant occurredAt) {
            this(UUID.randomUUID(), tenantId, surveyId, occurredAt);
        }

        @Override
        public String eventType() {
            return "survey.closed";
        }
    }

    /** scorePercent is null for non-quiz surveys. */
    record ResponseSubmitted(UUID eventId, TenantId tenantId, SurveyId surveyId, ResponseId responseId, Double scorePercent,
                             Instant occurredAt) implements DomainEvent {
        public ResponseSubmitted(TenantId tenantId, SurveyId surveyId, ResponseId responseId, Double scorePercent, Instant occurredAt) {
            this(UUID.randomUUID(), tenantId, surveyId, responseId, scorePercent, occurredAt);
        }

        @Override
        public String eventType() {
            return "survey.response.submitted";
        }
    }
}
