package tech.kayys.numou.education.pesantren.spi;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.education.pesantren.domain.SantriWallet;
import tech.kayys.numou.education.pesantren.domain.SantriWalletId;
import tech.kayys.numou.foundation.domain.repository.Repository;

public interface SantriWalletRepository extends Repository<SantriWallet, SantriWalletId> {
    CompletionStage<Optional<SantriWallet>> findBySantri(SantriId santriId);
}
