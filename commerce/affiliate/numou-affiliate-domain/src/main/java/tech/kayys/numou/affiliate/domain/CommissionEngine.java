package tech.kayys.numou.affiliate.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/** Pure commission calculation; does not post money, alter a wallet, or issue a payout. */
public final class CommissionEngine {
    private CommissionEngine() {}

    public static CommissionEntry calculate(
            UUID entryId, CommissionPlan plan, CommissionConversion conversion, Instant calculatedAt) {
        Objects.requireNonNull(entryId, "entryId is required");
        Objects.requireNonNull(plan, "plan is required");
        Objects.requireNonNull(conversion, "conversion is required");
        Objects.requireNonNull(calculatedAt, "calculatedAt is required");
        AttributionDecision attribution = conversion.attributionDecision();
        if (!plan.tenantId().equals(conversion.tenantId())
                || !plan.programId().equals(attribution.programId())) {
            throw new IllegalArgumentException("plan and conversion must share tenant and program scope");
        }
        if (!plan.isEffectiveAt(conversion.occurredAt())) {
            throw new IllegalArgumentException("commission plan was not effective at conversion time");
        }
        if (!plan.currency().equals(conversion.currency())) {
            throw new IllegalArgumentException("commission plan and eligible base currencies must match");
        }

        BigDecimal raw = switch (plan.type()) {
            case FIXED -> plan.value();
            case PERCENTAGE -> conversion.eligibleAmount()
                    .multiply(plan.value())
                    .divide(new BigDecimal("100"), 12, RoundingMode.HALF_EVEN);
        };
        int fractionDigits = Currency.getInstance(plan.currency()).getDefaultFractionDigits();
        if (fractionDigits < 0) fractionDigits = 2;
        BigDecimal amount = raw.setScale(fractionDigits, RoundingMode.HALF_EVEN);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("calculated commission rounds to zero");
        }
        return new CommissionEntry(entryId, conversion.tenantId(), attribution.programId(),
                attribution.partnerId(), attribution.id(), conversion.sourceType(), conversion.sourceId(),
                conversion.eventId(), plan.id(), plan.version(), plan.currency(),
                conversion.eligibleAmount(), amount, CommissionEntryType.EARNING, CommissionStatus.PENDING,
                null, "affiliate-commission:" + conversion.tenantId() + ":" + conversion.sourceType()
                + ":" + conversion.sourceId() + ":" + conversion.eventId() + ":" + plan.id() + ":" + plan.version(),
                calculatedAt);
    }

    public static CommissionEntry reverse(
            UUID reversalId, CommissionEntry original, String reversalEventId, Instant reversedAt) {
        Objects.requireNonNull(reversalId, "reversalId is required");
        Objects.requireNonNull(original, "original is required");
        Objects.requireNonNull(reversedAt, "reversedAt is required");
        if (original.type() != CommissionEntryType.EARNING) {
            throw new IllegalArgumentException("only an earning can be reversed");
        }
        if (original.status() == CommissionStatus.REVERSED) {
            throw new IllegalStateException("earning is already reversed");
        }
        String event = required(reversalEventId, "reversalEventId");
        return new CommissionEntry(reversalId, original.tenantId(), original.programId(), original.partnerId(),
                original.attributionDecisionId(), original.sourceType(), original.sourceId(),
                original.conversionEventId(), original.planId(), original.planVersion(), original.currency(),
                original.eligibleAmount(), original.commissionAmount().negate(), CommissionEntryType.REVERSAL,
                CommissionStatus.PENDING, original.id(), "affiliate-reversal:" + original.id() + ":" + event,
                reversedAt);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
