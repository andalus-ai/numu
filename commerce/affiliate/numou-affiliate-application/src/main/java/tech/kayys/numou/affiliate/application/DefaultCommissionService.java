package tech.kayys.numou.affiliate.application;

import tech.kayys.numou.affiliate.domain.CommissionConversion;
import tech.kayys.numou.affiliate.domain.CommissionEngine;
import tech.kayys.numou.affiliate.domain.CommissionEntry;
import tech.kayys.numou.affiliate.domain.CommissionPlan;
import tech.kayys.numou.affiliate.spi.CommissionEntryRepository;
import tech.kayys.numou.affiliate.spi.CommissionPlanRepository;
import tech.kayys.numou.affiliate.spi.CommissionableConversionSourcePort;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Coordinates authoritative source evidence, versioned plans, pure calculation, and atomic persistence. */
public final class DefaultCommissionService implements CommissionApplicationPort {
    private final CommissionableConversionSourcePort conversions;
    private final CommissionPlanRepository plans;
    private final CommissionEntryRepository entries;

    public DefaultCommissionService(
            CommissionableConversionSourcePort conversions,
            CommissionPlanRepository plans,
            CommissionEntryRepository entries) {
        this.conversions = Objects.requireNonNull(conversions, "conversions is required");
        this.plans = Objects.requireNonNull(plans, "plans is required");
        this.entries = Objects.requireNonNull(entries, "entries is required");
    }

    @Override
    public CompletionStage<CommissionEntry> calculateCommission(
            AffiliateCommandContext context, UUID programId, String sourceType,
            String sourceId, String conversionEventId) {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(programId, "programId is required");
        sourceType = required(sourceType, "sourceType");
        sourceId = required(sourceId, "sourceId");
        conversionEventId = required(conversionEventId, "conversionEventId");
        String tenantId = context.tenantId();
        String requestedType = sourceType;
        String requestedId = sourceId;
        String requestedEvent = conversionEventId;

        return conversions.findCommissionableConversion(tenantId, requestedType, requestedId, requestedEvent)
                .thenCompose(optional -> {
                    if (optional.isEmpty()) return failed(new IllegalStateException("Conversion is not verified as commissionable"));
                    CommissionConversion conversion = optional.get();
                    if (!conversion.tenantId().equals(tenantId)
                            || !conversion.sourceType().equals(requestedType)
                            || !conversion.sourceId().equals(requestedId)
                            || !conversion.eventId().equals(requestedEvent)) {
                        return failed(new IllegalStateException("Conversion source returned mismatched correlation identifiers"));
                    }
                    if (!conversion.attributionDecision().programId().equals(programId)) {
                        return failed(new IllegalStateException("Attribution decision belongs to a different program"));
                    }
                    return plans.findEffective(tenantId, programId, conversion.occurredAt())
                            .thenCompose(optionalPlan -> {
                                if (optionalPlan.isEmpty()) return failed(new IllegalStateException("No commission plan is effective for conversion time"));
                                CommissionPlan plan = optionalPlan.get();
                                if (!plan.tenantId().equals(tenantId) || !plan.programId().equals(programId)) {
                                    return failed(new IllegalStateException("Plan repository returned a cross-scope commission plan"));
                                }
                                try {
                                    CommissionEntry proposed = CommissionEngine.calculate(
                                            UUID.randomUUID(), plan, conversion, Instant.now());
                                    return entries.saveOrGet(proposed);
                                } catch (RuntimeException invalid) {
                                    return failed(invalid);
                                }
                            });
                });
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
