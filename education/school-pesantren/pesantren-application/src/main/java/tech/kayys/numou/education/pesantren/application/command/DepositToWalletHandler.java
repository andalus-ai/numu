package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.SantriWallet;
import tech.kayys.numou.education.pesantren.domain.SantriWalletId;
import tech.kayys.numou.education.pesantren.spi.SantriRepository;
import tech.kayys.numou.education.pesantren.spi.SantriWalletRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Deposits into a santri's wallet, opening the wallet on first use. */
public class DepositToWalletHandler {

    private final SantriRepository santris;
    private final SantriWalletRepository wallets;

    public DepositToWalletHandler(SantriRepository santris, SantriWalletRepository wallets) {
        this.santris = Objects.requireNonNull(santris);
        this.wallets = Objects.requireNonNull(wallets);
    }

    public CompletionStage<Result<SantriWalletId>> handle(DepositToWalletCommand cmd) {
        return santris.findById(cmd.santriId()).thenCompose(sOpt -> {
            if (sOpt.isEmpty() || !sOpt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<SantriWalletId>failed("pesantren.santri.not-found", "Santri not found");
            }
            if (!sOpt.get().isActive()) return Outcomes.<SantriWalletId>failed("pesantren.santri.inactive", "Santri is not active");
            return wallets.findBySantri(cmd.santriId()).thenCompose(existing -> {
                SantriWallet wallet;
                try {
                    wallet = existing.orElseGet(() -> SantriWallet.open(SantriWalletId.generate(), cmd.tenantId(), cmd.santriId(), cmd.dailyLimit()));
                    wallet.deposit(cmd.amount(), cmd.date(), cmd.note());
                } catch (IllegalArgumentException | NullPointerException e) {
                    return Outcomes.<SantriWalletId>failed("pesantren.wallet.invalid", e.getMessage());
                }
                return wallets.save(wallet).thenApply(saved -> Result.success(saved.getId()));
            });
        });
    }
}
