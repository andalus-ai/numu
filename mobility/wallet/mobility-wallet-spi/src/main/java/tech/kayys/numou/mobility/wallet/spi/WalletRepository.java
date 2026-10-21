package tech.kayys.numou.mobility.wallet.spi;

import tech.kayys.numou.aggregate.spi.VersionedRepository;
import tech.kayys.numou.mobility.wallet.domain.Wallet;
import tech.kayys.numou.mobility.wallet.domain.WalletId;

public interface WalletRepository extends VersionedRepository<Wallet, WalletId> { }
