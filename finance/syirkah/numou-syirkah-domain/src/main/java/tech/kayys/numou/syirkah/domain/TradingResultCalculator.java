package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.util.Objects;

/**
 * Works out the result of a trading syirkah for one period (Hukum Muamalah Bab XXII): the cost of goods sold (HPP)
 * is opening stock plus purchases minus closing stock, and it is the direct cost in the {@link FinancialResult} that
 * the profit split starts from.
 *
 * <p>This follows the book's periodic method, where stock is counted at the end of the period. Under that method
 * lost or stolen goods are not separated out: they sit inside the cost of goods sold. The caller therefore has to
 * give the counted closing stock, not a book figure.
 */
public final class TradingResultCalculator {

    private TradingResultCalculator() {
    }

    /**
     * @param sales             gross sales in the period
     * @param salesReturns      goods returned by customers
     * @param salesDiscounts    discounts given to customers
     * @param openingStock      value of stock at the start of the period
     * @param purchases         gross purchases in the period
     * @param purchaseReturns   goods returned to suppliers
     * @param purchaseDiscounts discounts received from suppliers
     * @param closingStock      value of the counted stock at the end of the period
     * @param operatingCosts    operating costs of the period
     * @param otherCosts        other costs of the period
     */
    public record Input(Money sales, Money salesReturns, Money salesDiscounts, Money openingStock, Money purchases,
                        Money purchaseReturns, Money purchaseDiscounts, Money closingStock, Money operatingCosts,
                        Money otherCosts) {
    }

    /** The figures behind the result, so a statement can show the book's layout. */
    public record Result(Money netSales, Money netPurchases, Money goodsAvailable, Money costOfGoodsSold,
                         Money grossProfit, FinancialResult financialResult) {
    }

    public static Result calculate(Input in) {
        Objects.requireNonNull(in, "input");
        Money netSales = in.sales().subtract(in.salesReturns()).subtract(in.salesDiscounts());
        Money netPurchases = in.purchases().subtract(in.purchaseReturns()).subtract(in.purchaseDiscounts());
        if (netSales.isNegative()) {
            throw new BusinessRuleViolation("returns and discounts exceed sales");
        }
        if (netPurchases.isNegative()) {
            throw new BusinessRuleViolation("returns and discounts exceed purchases");
        }
        Money available = in.openingStock().add(netPurchases);
        if (in.closingStock().compareTo(available) > 0) {
            throw new BusinessRuleViolation("closing stock " + in.closingStock().amount()
                    + " is more than the goods available for sale " + available.amount());
        }
        Money hpp = available.subtract(in.closingStock());
        Money gross = netSales.subtract(hpp);
        FinancialResult fr = FinancialResult.of(netSales, hpp, in.operatingCosts(), in.otherCosts());
        return new Result(netSales, netPurchases, available, hpp, gross, fr);
    }
}
