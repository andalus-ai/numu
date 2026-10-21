package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.SantriWalletId;
import tech.kayys.numou.education.pesantren.spi.SantriWalletRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class SpendFromWalletHandler {

    private final SantriWalletRepository wallets;

    public SpendFromWalletHandler(SantriWalletRepository wallets) {
        this.wallets = Objects.requireNonNull(wallets);
    }

    public CompletionStage<Result<SantriWalletId>> handle(SpendFromWalletCommand cmd) {
        return wallets.findBySantri(cmd.santriId()).thenCompose(opt -> {
            if (opt.isEmpty() || !opt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<SantriWalletId>failed("pesantren.wallet.not-found", "Santri has no wallet");
            }
            var wallet = opt.get();
            try {
                wallet.spend(cmd.amount(), cmd.date(), cmd.note());
            } catch (IllegalStateException | IllegalArgumentException | NullPointerException e) {
                return Outcomes.<SantriWalletId>failed("pesantren.wallet.cannot-spend", e.getMessage());
            }
            return wallets.save(wallet).thenApply(saved -> Result.success(saved.getId()));
        });
    }
}
