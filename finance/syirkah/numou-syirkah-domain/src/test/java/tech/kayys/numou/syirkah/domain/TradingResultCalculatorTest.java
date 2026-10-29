package tech.kayys.numou.numoudomain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.kayys.numou.numoudomain.Fixtures.idr;

class TradingResultCalculatorTest {

    private static TradingResultCalculator.Input input(long sales, long sRet, long sDisc, long open, long buy,
                                                       long bRet, long bDisc, long close, long opex, long other) {
        return new TradingResultCalculator.Input(idr(sales), idr(sRet), idr(sDisc), idr(open), idr(buy), idr(bRet),
                idr(bDisc), idr(close), idr(opex), idr(other));
    }

    @Test
    void costOfGoodsSoldIsOpeningPlusPurchasesMinusClosing() {
        var r = TradingResultCalculator.calculate(input(200, 10, 5, 50, 120, 8, 2, 40, 30, 0));
        assertEquals(0, idr(185).compareTo(r.netSales()));
        assertEquals(0, idr(110).compareTo(r.netPurchases()));
        assertEquals(0, idr(160).compareTo(r.goodsAvailable()));
        assertEquals(0, idr(120).compareTo(r.costOfGoodsSold())); // 50 + 110 - 40
        assertEquals(0, idr(65).compareTo(r.grossProfit()));
        assertEquals(0, idr(120).compareTo(r.financialResult().directCosts()));
        assertEquals(0, idr(185).compareTo(r.financialResult().revenue()));
        assertEquals(0, idr(35).compareTo(r.financialResult().netResult()));
    }

    @Test
    void aLossIsReportedAsANegativeResult() {
        var r = TradingResultCalculator.calculate(input(100, 0, 0, 0, 150, 0, 0, 20, 10, 0));
        assertEquals(0, idr(-40).compareTo(r.financialResult().netResult())); // 100 - 130 - 10
    }

    @Test
    void closingStockCannotExceedGoodsAvailable() {
        assertThrows(BusinessRuleViolation.class,
                () -> TradingResultCalculator.calculate(input(100, 0, 0, 10, 20, 0, 0, 31, 0, 0)));
    }

    @Test
    void returnsAndDiscountsCannotExceedTheirBase() {
        assertThrows(BusinessRuleViolation.class,
                () -> TradingResultCalculator.calculate(input(10, 8, 5, 0, 0, 0, 0, 0, 0, 0)));
        assertThrows(BusinessRuleViolation.class,
                () -> TradingResultCalculator.calculate(input(10, 0, 0, 0, 10, 8, 5, 0, 0, 0)));
    }
}
