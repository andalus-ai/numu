package tech.kayys.numou.wallet.adapter.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.aggregate.persistence.PgDocumentRepository;
import tech.kayys.numou.wallet.domain.Wallet;
import tech.kayys.numou.wallet.domain.WalletId;
import tech.kayys.numou.wallet.spi.WalletRepository;

/**
 * PostgreSQL-backed wallet aggregate repository.
 *
 * <p>Wallet snapshots share the framework's tenant-scoped, versioned aggregate-document store.
 * The repository uses the deterministic wallet aggregate id and leaves wallet-specific query
 * concerns to the ledger adapter.
 *
 * <p>Atomicity with ledger appends and the transactional outbox depends on the caller's unit of
 * work. Production readiness requires integration tests proving those writes share one transaction.
 */
@ApplicationScoped
public class PgWalletRepository extends PgDocumentRepository<Wallet, WalletId, Wallet.Snapshot>
        implements WalletRepository {

    @Inject
    public PgWalletRepository(ObjectMapper mapper) {
        super(mapper, "wallet", Wallet.Snapshot.class, Wallet::snapshot, Wallet::restore,
                wallet -> PgDocumentRepository.Index.NONE);
    }
}
