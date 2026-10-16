package tech.kayys.syirkah.project.domain.commercial;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.Objects;

/**
 * Original versus current contract value.
 *
 * The original value is the immutable baseline; the current value is
 * adjusted only through applied change orders, so the audit trail of
 * {@code original + approved changes = current} is preserved.
 */
public record ContractValue(
        Money original,
        Money current
) {

    public ContractValue {
        Objects.requireNonNull(original, "original cannot be null");
        Objects.requireNonNull(current, "current cannot be null");

        if (!original.currency().equals(current.currency())) {
            throw new IllegalArgumentException(
                    "Original and current contract value must use same currency"
            );
        }
    }

    public static ContractValue initial(Money value) {
        return new ContractValue(value, value);
    }
}