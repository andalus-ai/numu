package tech.kayys.syirkah.accounting.application.tax;

import tech.kayys.syirkah.accounting.domain.tax.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application service managing tax calculations, determination, and return filings.
 */
public final class TaxService {

    private final List<TaxTransaction> transactions = Collections.synchronizedList(new ArrayList<>());
    private final Map<TaxReturnId, TaxReturn> returns = new ConcurrentHashMap<>();

    public TaxTransaction computeAndRecord(TaxKind kind, TaxDirection direction,
                                           String jurisdiction, BigDecimal baseAmount,
                                           BigDecimal ratePct, String currency, String docRef) {
        BigDecimal taxAmount = baseAmount.multiply(ratePct)
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

        TaxTransaction tx = new TaxTransaction(
                TaxTransactionId.generate(), kind, direction, jurisdiction,
                baseAmount, ratePct, taxAmount, currency, docRef, Instant.now());
        transactions.add(tx);
        return tx;
    }

    public TaxReturn compileReturn(TaxReturnId id, TaxKind kind, String period, String jurisdiction) {
        BigDecimal output = transactions.stream()
                .filter(t -> t.kind() == kind && t.direction() == TaxDirection.OUTPUT_TAX && t.jurisdiction().equals(jurisdiction))
                .map(TaxTransaction::taxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal input = transactions.stream()
                .filter(t -> t.kind() == kind && t.direction() == TaxDirection.INPUT_TAX && t.jurisdiction().equals(jurisdiction))
                .map(TaxTransaction::taxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        TaxReturn ret = new TaxReturn(id, kind, period, jurisdiction);
        ret.updateTotals(output, input);
        returns.put(ret.id(), ret);
        return ret;
    }

    public void fileReturn(TaxReturnId id, String receipt) {
        TaxReturn ret = returns.get(id);
        if (ret == null) throw new IllegalArgumentException("TaxReturn not found: " + id.value());
        ret.file(receipt);
    }

    public Optional<TaxReturn> getReturn(TaxReturnId id) { return Optional.ofNullable(returns.get(id)); }
    public List<TaxTransaction> getTransactions() { return List.copyOf(transactions); }
}
