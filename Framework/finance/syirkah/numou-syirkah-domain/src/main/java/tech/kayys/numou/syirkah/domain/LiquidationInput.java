package tech.kayys.numou.syirkah.domain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Inputs of a liquidation: all assets converted to cash, then every external debt paid.
 *
 * @param assetProceeds    cash obtained from selling all assets (plus cash on hand)
 * @param externalDebts    all debts to third parties, paid before partners receive anything
 * @param shufahOffered    hak syuf'ah: partners were offered the assets before sale to outsiders
 * @param liquidationDate  date the liquidation is completed
 */
public record LiquidationInput(Money assetProceeds, Money externalDebts, boolean shufahOffered,
                               LocalDate liquidationDate) implements Serializable {
    public LiquidationInput {
        Objects.requireNonNull(assetProceeds, "assetProceeds");
        Objects.requireNonNull(externalDebts, "externalDebts");
        Objects.requireNonNull(liquidationDate, "liquidationDate");
        if (assetProceeds.isNegative() || externalDebts.isNegative()) {
            throw new IllegalArgumentException("proceeds and debts must be non-negative");
        }
    }
}
