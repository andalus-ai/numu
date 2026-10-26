package tech.kayys.numou.mobility.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.aggregate.persistence.PgDocumentRepository;
import tech.kayys.numou.mobility.wallet.domain.Wallet;
import tech.kayys.numou.mobility.wallet.domain.WalletId;
import tech.kayys.numou.mobility.wallet.spi.WalletRepository;

/**
 * PostgreSQL-backed wallet aggregate repository.
 *
 * <p>Wallet snapshots share the framework's tenant-scoped, versioned aggregate-document store.
 * The repository deliberately uses no wallet-specific secondary indexes: wallet lookup is by the
 * deterministic aggregate id derived from owner kind and owner id. The Mobility ledger remains a
 * separate append-only persistence concern.
 *
 * <p>This adapter depends on the caller's unit-of-work transaction for atomicity with ledger writes
 * and the transactional outbox. That transaction composition must be verified by integration tests
 * before this adapter is treated as production-ready.
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
