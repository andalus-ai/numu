package tech.kayys.numou.affiliate.application;

import tech.kayys.numou.affiliate.domain.AttributionDecision;
import tech.kayys.numou.affiliate.domain.AttributionEngine;
import tech.kayys.numou.affiliate.domain.AttributionPolicy;
import tech.kayys.numou.affiliate.domain.ConversionReference;
import tech.kayys.numou.affiliate.spi.AttributionDecisionRepository;
import tech.kayys.numou.affiliate.spi.AttributionPolicyRepository;
import tech.kayys.numou.affiliate.spi.AffiliateTouchpointRepository;
import tech.kayys.numou.affiliate.spi.QualifiedConversionSourcePort;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Coordinates attribution through public ports; the decision store is the atomic replay boundary. */
public final class DefaultAttributionService implements AttributionApplicationPort {
    private final QualifiedConversionSourcePort conversionSource;
    private final AttributionPolicyRepository policies;
    private final AffiliateTouchpointRepository touchpoints;
    private final AttributionDecisionRepository decisions;

    public DefaultAttributionService(
            QualifiedConversionSourcePort conversionSource,
            AttributionPolicyRepository policies,
            AffiliateTouchpointRepository touchpoints,
            AttributionDecisionRepository decisions) {
        this.conversionSource = Objects.requireNonNull(conversionSource, "conversionSource is required");
        this.policies = Objects.requireNonNull(policies, "policies is required");
        this.touchpoints = Objects.requireNonNull(touchpoints, "touchpoints is required");
        this.decisions = Objects.requireNonNull(decisions, "decisions is required");
    }

    @Override
    public CompletionStage<AttributionDecision> attributeConversion(
            AffiliateCommandContext context, UUID programId, String sourceType,
            String sourceId, String conversionEventId) {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(programId, "programId is required");
        sourceType = required(sourceType, "sourceType");
        sourceId = required(sourceId, "sourceId");
        conversionEventId = required(conversionEventId, "conversionEventId");
        final String tenantId = context.tenantId();
        final String requestedType = sourceType;
        final String requestedId = sourceId;
        final String requestedEvent = conversionEventId;

        return decisions.findBySourceEvent(tenantId, sourceType, sourceId, conversionEventId)
                .thenCompose(existing -> existing
                        .map(CompletableFuture::completedFuture)
                        .orElseGet(() -> resolveAndDecide(
                                tenantId, programId, requestedType, requestedId, requestedEvent)));
    }

    private CompletionStage<AttributionDecision> resolveAndDecide(
            String tenantId, UUID programId, String sourceType, String sourceId, String eventId) {
        return conversionSource.findQualifiedConversion(tenantId, sourceType, sourceId, eventId)
                .thenCompose(optionalConversion -> {
                    if (optionalConversion.isEmpty()) {
                        return failed(new IllegalStateException("Source conversion is not verified as qualified"));
                    }
                    ConversionReference conversion = optionalConversion.get();
                    if (!conversion.sourceType().equals(sourceType)
                            || !conversion.sourceId().equals(sourceId)
                            || !conversion.eventId().equals(eventId)) {
                        return failed(new IllegalStateException("Conversion source returned mismatched correlation identifiers"));
                    }
                    return policies.findEffective(tenantId, programId, conversion.occurredAt())
                            .thenCompose(optionalPolicy -> {
                                if (optionalPolicy.isEmpty()) {
                                    return failed(new IllegalStateException("No attribution policy is effective for conversion time"));
                                }
                                AttributionPolicy policy = optionalPolicy.get();
                                if (!policy.tenantId().equals(tenantId) || !policy.programId().equals(programId)) {
                                    return failed(new IllegalStateException("Policy repository returned a cross-scope policy"));
                                }
                                Instant notBefore = subtractWindow(conversion.occurredAt(), policy.windowSeconds());
                                return touchpoints.findEligible(tenantId, programId, conversion.subjectKey(),
                                                notBefore, conversion.occurredAt())
                                        .thenCompose(candidates -> {
                                            AttributionDecision proposed = AttributionEngine.decide(
                                                    UUID.randomUUID(), tenantId, policy, conversion,
                                                    candidates, Instant.now());
                                            return decisions.saveOrGet(proposed);
                                        });
                            });
                });
    }

    private static Instant subtractWindow(Instant instant, long seconds) {
        try {
            return instant.minusSeconds(seconds);
        } catch (DateTimeException | ArithmeticException ex) {
            return Instant.MIN;
        }
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
