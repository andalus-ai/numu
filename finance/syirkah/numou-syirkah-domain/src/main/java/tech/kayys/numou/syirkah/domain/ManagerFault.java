package tech.kayys.numou.syirkah.domain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;
import java.util.Objects;

/**
 * Finding that a mudharib acted negligently or breached the akad. Only then does
 * (part of) a mudharabah loss move from the capital provider to the manager.
 *
 * @param attributableLoss the part of the period's loss caused by the fault
 */
public record ManagerFault(boolean negligentOrBreach, Money attributableLoss, String reason)
        implements Serializable {

    public ManagerFault {
        if (negligentOrBreach) {
            Objects.requireNonNull(attributableLoss, "attributableLoss");
            if (attributableLoss.isNegative()) {
                throw new IllegalArgumentException("attributable loss cannot be negative");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("a finding of fault must state its reason");
            }
        }
    }

    public static ManagerFault none() {
        return new ManagerFault(false, null, null);
    }

    public static ManagerFault of(Money attributableLoss, String reason) {
        return new ManagerFault(true, attributableLoss, reason);
    }
}
