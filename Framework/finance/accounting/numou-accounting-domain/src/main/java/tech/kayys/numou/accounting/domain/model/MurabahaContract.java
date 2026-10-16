package tech.kayys.syirkah.accounting.domain.model;

import tech.kayys.syirkah.accounting.domain.event.MurabahaCreated;
import tech.kayys.syirkah.accounting.domain.event.MurabahaSettled;
import tech.kayys.syirkah.accounting.domain.event.ProfitRecognized;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate representing a Murabahah (cost-plus) Islamic financing contract.
 *
 * <p>A Murabahah contract is a sale at a disclosed cost-plus margin. The bank
 * purchases an asset at {@code cost} and sells it to the customer at
 * {@code sellingPrice} (= cost + margin) payable in instalments over
 * {@code tenorMonths}. The profit is recognised over the tenor period
 * using PSAK 102 straight-line method (configurable to effective-profit-rate).
 *
 * <p>No riba (interest) is charged; profit recognition is deferred.
 */
public final class MurabahaContract {

    private final String contractId;
    private final TenantRef tenantId;
    private final LedgerId ledgerId;
    private final BigDecimal cost;
    private final BigDecimal margin;
    private final BigDecimal sellingPrice;
    private final int tenorMonths;

    private boolean settled;
    private BigDecimal deferredProfit;
    private int recognizedPeriods;

    private final List<Object> domainEvents = new ArrayList<>();

    private MurabahaContract(
            String contractId,
            TenantRef tenantId,
            LedgerId ledgerId,
            BigDecimal cost,
            BigDecimal margin,
            int tenorMonths) {
        Objects.requireNonNull(contractId, "contractId required");
        Objects.requireNonNull(tenantId,   "tenantId required");
        Objects.requireNonNull(ledgerId,   "ledgerId required");
        Objects.requireNonNull(cost,       "cost required");
        Objects.requireNonNull(margin,     "margin required");

        if (cost.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("cost must be positive");
        if (margin.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("margin must be non-negative");
        if (tenorMonths < 1)
            throw new IllegalArgumentException("tenorMonths must be >= 1");

        this.contractId     = contractId;
        this.tenantId       = tenantId;
        this.ledgerId       = ledgerId;
        this.cost           = cost;
        this.margin         = margin;
        this.sellingPrice   = cost.add(margin);
        this.tenorMonths    = tenorMonths;
        this.deferredProfit = margin;
        this.settled        = false;
        this.recognizedPeriods = 0;

        raise(MurabahaCreated.of(tenantId, ledgerId, contractId,
                cost, margin, sellingPrice, tenorMonths,
                contractId, // correlationId = contractId
                null));
    }

    /** Factory — creates and validates the contract, raises {@link MurabahaCreated}. */
    public static MurabahaContract create(
            String contractId,
            TenantRef tenantId,
            LedgerId ledgerId,
            BigDecimal cost,
            BigDecimal margin,
            int tenorMonths) {
        return new MurabahaContract(contractId, tenantId, ledgerId, cost, margin, tenorMonths);
    }

    /**
     * Recognise one period's profit (PSAK 102 straight-line).
     * Raises {@link ProfitRecognized}.
     */
    public void recognizePeriodProfit() {
        if (settled)
            throw new IllegalStateException("Contract is already settled");
        if (recognizedPeriods >= tenorMonths)
            throw new IllegalStateException("All periods already recognised");

        BigDecimal periodProfit = margin
                .divide(BigDecimal.valueOf(tenorMonths), 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);

        // Last period: assign exact remaining amount to avoid rounding leakage
        if (recognizedPeriods == tenorMonths - 1) {
            periodProfit = deferredProfit;
        }

        recognizedPeriods++;
        deferredProfit = deferredProfit.subtract(periodProfit);

        raise(ProfitRecognized.of(tenantId, ledgerId, contractId,
                recognizedPeriods, periodProfit, deferredProfit,
                contractId, null));
    }

    /**
     * Settle the contract. Raises {@link MurabahaSettled}.
     * Any remaining unrecognised profit is recognised immediately on early settlement.
     */
    public void settle() {
        if (settled)
            throw new IllegalStateException("Contract already settled");

        while (recognizedPeriods < tenorMonths) {
            recognizePeriodProfit();
        }

        settled = true;
        raise(MurabahaSettled.of(tenantId, ledgerId, contractId, contractId, null));
    }

    // ─── Getters ────────────────────────────────────────────────────────────

    public String contractId()         { return contractId; }
    public TenantRef tenantId()         { return tenantId; }
    public LedgerId ledgerId()         { return ledgerId; }
    public BigDecimal cost()           { return cost; }
    public BigDecimal margin()         { return margin; }
    public BigDecimal sellingPrice()   { return sellingPrice; }
    public int tenorMonths()           { return tenorMonths; }
    public boolean settled()           { return settled; }
    public BigDecimal deferredProfit() { return deferredProfit; }
    public int recognizedPeriods()     { return recognizedPeriods; }

    // ─── Domain event helpers ────────────────────────────────────────────────

    private void raise(Object event) { domainEvents.add(event); }

    public List<Object> pullDomainEvents() {
        List<Object> copy = List.copyOf(domainEvents);
        domainEvents.clear();
        return copy;
    }
}
