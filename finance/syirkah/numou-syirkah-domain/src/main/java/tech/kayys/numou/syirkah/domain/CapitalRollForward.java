package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Per-partner roll-forward of capital balance over a time window: opening +
 * changes = closing.
 */
public record CapitalRollForward(Instant from, Instant to, List<Row> rows) implements Serializable {

    public CapitalRollForward {
        rows = List.copyOf(rows);
    }

    public record Row(PartnerId partner, String name, Money opening, Money contributions, Money returns,
            Money retainedProfit, Money capitalRestored, Money lossesAllocated, Money deficitReclass,
            Money closing) implements Serializable {
    }
}
