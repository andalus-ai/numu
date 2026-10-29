package tech.kayys.numou.numoudomain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.kayys.numou.numoudomain.Fixtures.*;

class BookStatementsTest {

    private static final LocalDate JAN1 = LocalDate.parse("2026-01-01");
    private static final LocalDate JAN31 = LocalDate.parse("2026-01-31");

    @Test
    void balanceSheetAtOpeningBalances() {
        Syirkah s = open(inan(100_000_000, 100_000_000, ratio(A, 50, B, 50)));
        var bs = BookStatements.balanceSheet(s, "RM Barokah", JAN1,
                List.of(new BookStatements.AssetLine(BookStatements.AssetGroup.CURRENT, "Cash", idr(200_000_000))),
                List.of());
        assertEquals(0, idr(200_000_000).compareTo(bs.totalAssets()));
        assertEquals(0, idr(200_000_000).compareTo(bs.totalCapital()));
        assertTrue(bs.balanced(), bs.difference().toString());
        assertEquals(2, bs.capital().size());
    }

    @Test
    void balanceSheetAfterAProfitWithPayableAndAnOutsideDebt() {
        Syirkah s = open(inan(100_000_000, 100_000_000, ratio(A, 50, B, 50)));
        s.closePeriod(CloseId.generate(), JAN1, JAN31, profit(10_000_000), ManagerFault.none(),
                ProfitDisposition.DISTRIBUTE, NOW);
        // cash 200m + 10m profit; the syirkah also owes a supplier 4m for stock worth 4m
        var bs = BookStatements.balanceSheet(s, "RM Barokah", JAN31,
                List.of(new BookStatements.AssetLine(BookStatements.AssetGroup.CURRENT, "Cash", idr(210_000_000)),
                        new BookStatements.AssetLine(BookStatements.AssetGroup.CURRENT, "Stock", idr(4_000_000))),
                List.of(new BookStatements.LiabilityLine(BookStatements.LiabilityGroup.SHORT_TERM, "Supplier",
                        idr(4_000_000))));
        assertEquals(0, idr(214_000_000).compareTo(bs.totalAssets()));
        assertEquals(0, idr(210_000_000).compareTo(bs.totalCapital())); // 200m capital + 10m payable
        assertTrue(bs.balanced(), bs.difference().toString());
    }

    @Test
    void aWrongAssetFigureShowsAsADifference() {
        Syirkah s = open(inan(100_000_000, 100_000_000, ratio(A, 50, B, 50)));
        var bs = BookStatements.balanceSheet(s, "X", JAN1,
                List.of(new BookStatements.AssetLine(BookStatements.AssetGroup.CURRENT, "Cash", idr(199_000_000))),
                List.of());
        assertFalse(bs.balanced());
        assertEquals(0, idr(-1_000_000).compareTo(bs.difference()));
    }

    @Test
    void incomeStatementFollowsTheBooksOrder() {
        Syirkah s = open(inan(100_000_000, 100_000_000, ratio(A, 50, B, 50)));
        var c = s.closePeriod(CloseId.generate(), JAN1, JAN31, result(30_000_000, 12_000_000, 5_000_000, 1_000_000),
                ManagerFault.none(), ProfitDisposition.RETAIN, NOW);
        var is = BookStatements.incomeStatement("RM Barokah", c);
        assertEquals(0, idr(18_000_000).compareTo(is.grossProfit()));
        assertEquals(0, idr(12_000_000).compareTo(is.netResult()));
        assertEquals("PROFIT", is.result());
    }

    @Test
    void aLossIsLabelledAsALoss() {
        Syirkah s = open(inan(100_000_000, 100_000_000, ratio(A, 50, B, 50)));
        var c = s.closePeriod(CloseId.generate(), JAN1, JAN31, loss(4_000_000), ManagerFault.none(),
                ProfitDisposition.RETAIN, NOW);
        assertEquals("LOSS", BookStatements.incomeStatement("X", c).result());
    }
}
