package tech.kayys.numou.workforce.domain.compensation;

import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.workforce.domain.paycomponent.PayComponentId;

import java.util.Objects;

public record CompensationComponent(
        PayComponentId componentId,
        Money amount,
        CompensationComponentType type
) {
    public CompensationComponent {
        Objects.requireNonNull(componentId, "componentId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (amount.isNegative()) {
            throw new IllegalArgumentException("CompensationComponent amount cannot be negative");
        }
    }
}
