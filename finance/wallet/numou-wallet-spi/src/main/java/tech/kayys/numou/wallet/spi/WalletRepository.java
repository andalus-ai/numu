package tech.kayys.numou.wallet.spi;

import tech.kayys.numou.aggregate.spi.VersionedRepository;
import tech.kayys.numou.wallet.domain.Wallet;
import tech.kayys.numou.wallet.domain.WalletId;

public interface WalletRepository extends VersionedRepository<Wallet, WalletId> { }
