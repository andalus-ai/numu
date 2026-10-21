package tech.kayys.numou.mobility.integration;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.mobility.ride.spi.PaymentsPort;
import tech.kayys.numou.mobility.wallet.application.WalletService;
import tech.kayys.numou.mobility.wallet.domain.OwnerKind;

import java.math.BigDecimal;
import java.util.UUID;

/** {@link PaymentsPort} implemented on the wallet context. Keeping the mapping here is what lets rides and wallets stay ignorant of each other. */
public final class WalletBackedPayments implements PaymentsPort {

    private final WalletService wallets;

    public WalletBackedPayments(WalletService wallets) {
        this.wallets = wallets;
    }

    @Override
    public Uni<Void> authorize(UUID tenantId, UUID riderId, String holdRef, BigDecimal amount) {
        return wallets.hold(tenantId, OwnerKind.RIDER, riderId, amount, holdRef).replaceWithVoid();
    }

    @Override
    public Uni<Void> releaseHold(UUID tenantId, UUID riderId, String holdRef, String ref) {
        return wallets.release(tenantId, OwnerKind.RIDER, riderId, holdRef, ref).replaceWithVoid();
    }

    @Override
    public Uni<Boolean> riderCanBook(UUID tenantId, UUID riderId) {
        return wallets.view(tenantId, OwnerKind.RIDER, riderId).map(v -> v.canBook());
    }

    @Override
    public Uni<Boolean> driverCanAcceptCash(UUID tenantId, UUID driverId) {
        return wallets.view(tenantId, OwnerKind.DRIVER, driverId).map(v -> v.canAcceptCash());
    }

    @Override
    public Uni<Void> chargeRider(UUID tenantId, UUID riderId, String holdRef, BigDecimal amount, String ref, String memo) {
        return wallets.capture(tenantId, OwnerKind.RIDER, riderId, amount, holdRef, ref, memo).replaceWithVoid();
    }

    @Override
    public Uni<Void> creditRider(UUID tenantId, UUID riderId, BigDecimal amount, String ref, String memo) {
        return wallets.credit(tenantId, OwnerKind.RIDER, riderId, amount, ref, memo).replaceWithVoid();
    }

    @Override
    public Uni<Void> debitRiderStrict(UUID tenantId, UUID riderId, BigDecimal amount, String ref, String memo) {
        return wallets.debit(tenantId, OwnerKind.RIDER, riderId, amount, ref, memo).replaceWithVoid();
    }

    @Override
    public Uni<Void> moveDriver(UUID tenantId, UUID driverId, BigDecimal signedAmount, String ref, String memo) {
        return signedAmount.signum() >= 0
                ? wallets.credit(tenantId, OwnerKind.DRIVER, driverId, signedAmount, ref, memo).replaceWithVoid()
                // the cash was already collected, so the platform's share is owed regardless of the overdraft limit
                : wallets.capture(tenantId, OwnerKind.DRIVER, driverId, signedAmount.negate(), "none:" + ref, ref, memo).replaceWithVoid();
    }

    @Override
    public Uni<Void> movePlatform(UUID tenantId, UUID shardKey, BigDecimal signedAmount, String ref, String memo) {
        return wallets.platformMove(tenantId, shardKey, signedAmount, ref, memo).replaceWithVoid();
    }
}
