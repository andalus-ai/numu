package tech.kayys.numou.finance.treasury.domain.model;

import tech.kayys.numou.finance.treasury.domain.identifier.CashMovementId;
import tech.kayys.numou.finance.treasury.domain.identifier.DrawerSessionId;
import tech.kayys.numou.finance.treasury.domain.valueobject.CashMovementType;
import tech.kayys.numou.finance.treasury.domain.valueobject.DrawerStatus;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root for cash drawer / cashier shifts.
 * Core for POS, Coffee Shop, Retail cash management and end-of-shift Z-reports.
 */
public final class CashDrawerSession extends AbstractAggregateRoot<DrawerSessionId> {

    private String registerId;
    private String cashierId;
    private String currencyCode;
    private DrawerStatus status;

    private BigDecimal openingFloat;
    private BigDecimal totalCashSales;
    private BigDecimal totalCashRefunds;
    private BigDecimal totalCashIn;
    private BigDecimal totalCashOut;
    private BigDecimal actualClosingCash;
    private BigDecimal discrepancy; // positive = over, negative = short

    private Instant openedAt;
    private Instant closedAt;
    private String closingNotes;

    private final List<CashMovement> movements = new ArrayList<>();

    private CashDrawerSession(DrawerSessionId id) {
        super(id);
    }

    public static CashDrawerSession open(
            DrawerSessionId id,
            String registerId,
            String cashierId,
            String currencyCode,
            BigDecimal openingFloat) {
        if (openingFloat == null || openingFloat.signum() < 0) {
            throw new IllegalArgumentException("Opening float must be non-negative");
        }
        CashDrawerSession session = new CashDrawerSession(id);
        session.registerId = registerId;
        session.cashierId = cashierId;
        session.currencyCode = currencyCode != null ? currencyCode : "USD";
        session.openingFloat = openingFloat;
        session.totalCashSales = BigDecimal.ZERO;
        session.totalCashRefunds = BigDecimal.ZERO;
        session.totalCashIn = BigDecimal.ZERO;
        session.totalCashOut = BigDecimal.ZERO;
        session.status = DrawerStatus.OPEN;
        session.openedAt = Instant.now();
        return session;
    }

    public void recordCashSale(BigDecimal amount) {
        assertOpen();
        this.totalCashSales = this.totalCashSales.add(amount);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void recordCashRefund(BigDecimal amount) {
        assertOpen();
        this.totalCashRefunds = this.totalCashRefunds.add(amount);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void recordMovement(CashMovementType type, BigDecimal amount, String reason, String authorizedBy) {
        assertOpen();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Movement amount must be positive");
        }
        if (type == CashMovementType.CASH_IN || type == CashMovementType.FLOAT_ADJUSTMENT) {
            this.totalCashIn = this.totalCashIn.add(amount);
        } else {
            this.totalCashOut = this.totalCashOut.add(amount);
        }
        movements.add(new CashMovement(
            CashMovementId.generate(),
            getId(),
            type,
            amount,
            currencyCode,
            reason,
            authorizedBy,
            Instant.now()
        ));
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public BigDecimal calculateExpectedCash() {
        return openingFloat
                .add(totalCashSales)
                .subtract(totalCashRefunds)
                .add(totalCashIn)
                .subtract(totalCashOut);
    }

    public void close(BigDecimal actualCountedCash, String notes) {
        assertOpen();
        if (actualCountedCash == null || actualCountedCash.signum() < 0) {
            throw new IllegalArgumentException("Actual counted cash cannot be negative");
        }
        this.actualClosingCash = actualCountedCash;
        this.discrepancy = actualCountedCash.subtract(calculateExpectedCash());
        this.closingNotes = notes;
        this.closedAt = Instant.now();
        this.status = DrawerStatus.CLOSED;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    private void assertOpen() {
        if (status != DrawerStatus.OPEN) {
            throw new IllegalStateException("Drawer session is not open");
        }
    }

    public String getRegisterId() { return registerId; }
    public String getCashierId() { return cashierId; }
    public String getCurrencyCode() { return currencyCode; }
    public DrawerStatus getStatus() { return status; }
    public BigDecimal getOpeningFloat() { return openingFloat; }
    public BigDecimal getTotalCashSales() { return totalCashSales; }
    public BigDecimal getTotalCashRefunds() { return totalCashRefunds; }
    public BigDecimal getTotalCashIn() { return totalCashIn; }
    public BigDecimal getTotalCashOut() { return totalCashOut; }
    public BigDecimal getActualClosingCash() { return actualClosingCash; }
    public BigDecimal getDiscrepancy() { return discrepancy; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public String getClosingNotes() { return closingNotes; }
    public List<CashMovement> getMovements() { return Collections.unmodifiableList(movements); }
}
