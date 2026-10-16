package tech.kayys.syirkah.accounting.domain.ledger;

/**
 * Contract implemented by all aggregates belonging to a specific financial ledger.
 */
public interface LedgerAware {
    LedgerId ledgerId();
}
