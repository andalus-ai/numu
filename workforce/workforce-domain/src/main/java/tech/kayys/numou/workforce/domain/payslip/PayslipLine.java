package tech.kayys.numou.workforce.domain.payslip;

import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.workforce.domain.paycomponent.PayComponentId;

import java.util.Objects;
import java.util.UUID;

/**
 * A single line item on a Payslip — either an earning or a deduction.
 */
public record PayslipLine(
        UUID id,
        PayComponentId componentId,
        String code,
        String description,
        PayslipLineType type,
        Money amount
) {
    public PayslipLine {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(componentId, "componentId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.isNegative()) {
            throw new IllegalArgumentException("PayslipLine amount cannot be negative");
        }
    }

    public static PayslipLine of(
            PayComponentId componentId,
            String code,
            String description,
            PayslipLineType type,
            Money amount
    ) {
        return new PayslipLine(UUID.randomUUID(), componentId, code, description, type, amount);
    }

    public boolean isEarning() { return type == PayslipLineType.EARNING; }
    public boolean isDeduction() { return type == PayslipLineType.DEDUCTION; }
}
