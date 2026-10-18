package tech.kayys.numou.syirkah.domain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;
import java.time.Instant;

/** One row of a partner statement with the running position after the movement. */
public record StatementLine(long sequence, Instant at, MovementType type, String reference, Money amount,
                            Money capitalBalance, Money profitPayable, Money liability) implements Serializable {
}
