package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Inputs of a liquidation: all assets converted to cash, then every external
 * debt paid.
 *
 * @param assetProceeds   cash obtained from selling all assets (plus cash on
 *                        hand)
 * @param externalDebts   all debts to third parties, paid before partners
 *                        receive anything
 * @param shufahOffered   hak syuf'ah: partners were offered the assets before
 *                        sale to outsiders
 * @param liquidationDate date the liquidation is completed
 * @param sales            optional asset-by-asset breakdown. When present the proceeds must equal their sum and
 *                         {@code shufahOffered} must say whether every sale to an outsider respected hak syuf'ah
 */
public record LiquidationInput(Money assetProceeds, Money externalDebts, boolean shufahOffered,
                               LocalDate liquidationDate, List<AssetSale> sales) implements Serializable {

    public LiquidationInput(Money assetProceeds, Money externalDebts, boolean shufahOffered,
                            LocalDate liquidationDate) {
        this(assetProceeds, externalDebts, shufahOffered, liquidationDate, List.of());
    }

    /** Builds the input from the individual sales: proceeds and the syuf'ah flag are derived, never typed twice. */
    public static LiquidationInput fromSales(List<AssetSale> sales, Money externalDebts, LocalDate liquidationDate) {
        Objects.requireNonNull(sales, "sales");
        if (sales.isEmpty()) {
            throw new IllegalArgumentException("at least one asset sale is required");
        }
        Money total = Money.zero(sales.get(0).proceeds().currency());
        for (AssetSale sale : sales) {
            total = total.add(sale.proceeds());
        }
        boolean ok = sales.stream().allMatch(AssetSale::respectsShufah);
        return new LiquidationInput(total, externalDebts, ok, liquidationDate, sales);
    }

    public LiquidationInput {
        sales = sales == null ? List.of() : List.copyOf(sales);
        Objects.requireNonNull(assetProceeds, "assetProceeds");
        Objects.requireNonNull(externalDebts, "externalDebts");
        Objects.requireNonNull(liquidationDate, "liquidationDate");
        if (assetProceeds.isNegative() || externalDebts.isNegative()) {
            throw new IllegalArgumentException("proceeds and debts must be non-negative");
        }
        if (!sales.isEmpty()) {
            Money total = Money.zero(assetProceeds.currency());
            for (AssetSale sale : sales) {
                Objects.requireNonNull(sale, "sale");
                if (!sale.proceeds().currency().equals(assetProceeds.currency())) {
                    throw new IllegalArgumentException("all asset sales must share the proceeds currency");
                }
                total = total.add(sale.proceeds());
            }
            if (total.compareTo(assetProceeds) != 0) {
                throw new IllegalArgumentException("asset proceeds must equal the sum of the individual sales");
            }
            if (shufahOffered != sales.stream().allMatch(AssetSale::respectsShufah)) {
                throw new IllegalArgumentException("shufahOffered contradicts the individual sales");
            }
        }
    }
}
