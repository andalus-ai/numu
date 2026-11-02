package tech.kayys.numou.numouapplication.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numoudomain.BookStatements;
import tech.kayys.numou.numoudomain.SyirkahId;

import java.util.List;
import java.util.Optional;

/**
 * Supplies the assets and outside debts of a syirkah from the business's own books, so the balance sheet does not
 * depend on the caller typing them in. The syirkah's own equity accounts are never included: the ledger adds
 * partner receivables and shows capital itself.
 */
public interface BusinessAssetsPort {

    /** Empty when the syirkah has no books of its own to read. */
    Optional<Snapshot> forSyirkah(TenantId tenant, SyirkahId syirkah);

    record Snapshot(List<BookStatements.AssetLine> assets, List<BookStatements.LiabilityLine> liabilities) {
    }
}
