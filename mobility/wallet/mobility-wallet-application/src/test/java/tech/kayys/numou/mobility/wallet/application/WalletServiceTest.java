package tech.kayys.numou.mobility.wallet.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.aggregate.memory.RecordingEventSink;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.mobility.wallet.adapter.memory.InMemoryWalletRepositories;
import tech.kayys.numou.mobility.wallet.domain.InsufficientFundsException;
import tech.kayys.numou.mobility.wallet.domain.OwnerKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletServiceTest {

    static BigDecimal bd(String s) { return new BigDecimal(s); }
    final UUID tenant = UUID.randomUUID();
    final UnitOfWork uow = new UnitOfWork() { public <R> Uni<R> execute(java.util.function.Supplier<Uni<R>> w) { return w.get(); } };
    final RecordingEventSink sink = new RecordingEventSink();
    InMemoryWalletRepositories.Ledger ledger;
    WalletService svc;

    @BeforeEach
    void setUp() {
        ledger = new InMemoryWalletRepositories.Ledger();
        svc = new WalletService(new InMemoryWalletRepositories.Wallets(), ledger, uow, sink, Instant::now, "IDR", 0, bd("100000"), bd("50000"));
    }

    @Test
    void topUpOpensTheWalletAndIsIdempotent() {
        final var rider = UUID.randomUUID();
        svc.credit(tenant, OwnerKind.RIDER, rider, bd("100000"), "topup:gw-1", "top-up").await().indefinitely();
        final var again = svc.credit(tenant, OwnerKind.RIDER, rider, bd("100000"), "topup:gw-1", "top-up").await().indefinitely();
        assertThat(again.balance()).isEqualByComparingTo("100000");
        assertThat(ledger.size()).isEqualTo(1);
    }

    @Test
    void reusingAnIdempotencyReferenceWithDifferentPayloadIsRejected() {
        final var rider = UUID.randomUUID();
        svc.credit(tenant, OwnerKind.RIDER, rider, bd("1000"), "stable-key", "top-up").await().indefinitely();
        assertThatThrownBy(() -> svc.credit(tenant, OwnerKind.RIDER, rider, bd("2000"), "stable-key", "top-up").await().indefinitely())
                .isInstanceOf(BusinessRuleViolation.class);
        assertThat(svc.view(tenant, OwnerKind.RIDER, rider).await().indefinitely().balance()).isEqualByComparingTo("1000");
        assertThat(ledger.size()).isEqualTo(1);
    }

    @Test
    void oldReferenceRemainsIdempotentAfterAggregateReferenceCacheEvictsIt() {
        final var rider = UUID.randomUUID();
        svc.credit(tenant, OwnerKind.RIDER, rider, bd("1000"), "old-key", "top-up").await().indefinitely();
        for (int i = 0; i < 510; i++) svc.credit(tenant, OwnerKind.RIDER, rider, bd("1"), "later-" + i, "top-up").await().indefinitely();
        final var replay = svc.credit(tenant, OwnerKind.RIDER, rider, bd("1000"), "old-key", "top-up").await().indefinitely();
        assertThat(replay.balance()).isEqualByComparingTo("1510");
        assertThat(ledger.size()).isEqualTo(511);
    }

    @Test
    void holdCaptureReleaseFlowWritesALedger() {
        final var rider = UUID.randomUUID();
        svc.credit(tenant, OwnerKind.RIDER, rider, bd("100000"), "t", "").await().indefinitely();
        svc.hold(tenant, OwnerKind.RIDER, rider, bd("30000"), "ride:1:hold").await().indefinitely();
        final var v = svc.capture(tenant, OwnerKind.RIDER, rider, bd("27000"), "ride:1:hold", "ride:1:capture", "ride").await().indefinitely();
        assertThat(v.balance()).isEqualByComparingTo("73000");
        assertThat(v.held()).isEqualByComparingTo("0");
        assertThat(svc.history(tenant, OwnerKind.RIDER, rider, 10).await().indefinitely()).extracting(e -> e.kind().name())
                .containsExactlyInAnyOrder("CREDIT", "HOLD", "CAPTURE");
    }

    @Test
    void insufficientBalanceRefusesTheHold() {
        final var rider = UUID.randomUUID();
        assertThatThrownBy(() -> svc.hold(tenant, OwnerKind.RIDER, rider, bd("1000"), "h").await().indefinitely()).isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void payoutNeedsMinimumAndRealBalanceNeverTheOverdraft() {
        final var driver = UUID.randomUUID();
        svc.credit(tenant, OwnerKind.DRIVER, driver, bd("60000"), "e1", "").await().indefinitely();
        assertThatThrownBy(() -> svc.requestPayout(tenant, driver, bd("40000"), "p1").await().indefinitely()).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> svc.requestPayout(tenant, driver, bd("70000"), "p2").await().indefinitely()).isInstanceOf(InsufficientFundsException.class);
        assertThat(svc.requestPayout(tenant, driver, bd("60000"), "p3").await().indefinitely().balance()).isEqualByComparingTo("0");
    }

    @Test
    void walletsAreTenantIsolated() {
        final var owner = UUID.randomUUID();
        svc.credit(tenant, OwnerKind.RIDER, owner, bd("5000"), "t", "").await().indefinitely();
        assertThat(svc.view(UUID.randomUUID(), OwnerKind.RIDER, owner).await().indefinitely().balance()).isEqualByComparingTo("0");
    }

    @Test
    void concurrentCreditsToOneWalletNeverLoseMoney() throws Exception {
        for (int round = 0; round < 10; round++) {
            setUp();
            final var rider = UUID.randomUUID();
            final int writers = 6;
            final var pool = Executors.newFixedThreadPool(writers); final var go = new CountDownLatch(1);
            final List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < writers; i++) {
                final int n = i;
                fs.add(pool.submit(() -> { go.await(); svc.credit(tenant, OwnerKind.RIDER, rider, bd("1000"), "c" + n, "").await().indefinitely(); return null; }));
            }
            go.countDown();
            for (var f : fs) f.get(10, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(svc.view(tenant, OwnerKind.RIDER, rider).await().indefinitely().balance()).as("round %d", round).isEqualByComparingTo("6000");
        }
    }

    @Test
    void sameRefSubmittedConcurrentlyIsAppliedOnce() throws Exception {
        for (int round = 0; round < 10; round++) {
            setUp();
            final var rider = UUID.randomUUID();
            final var pool = Executors.newFixedThreadPool(6); final var go = new CountDownLatch(1);
            final List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < 6; i++) fs.add(pool.submit(() -> { go.await(); svc.credit(tenant, OwnerKind.RIDER, rider, bd("1000"), "topup:same", "").await().indefinitely(); return null; }));
            go.countDown();
            for (var f : fs) f.get(10, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(svc.view(tenant, OwnerKind.RIDER, rider).await().indefinitely().balance()).as("round %d", round).isEqualByComparingTo("1000");
        }
    }

    @Test
    void platformRevenueSpreadsAcrossShardsAndTotalsCorrectly() {
        for (int i = 0; i < 200; i++) svc.platformMove(tenant, UUID.randomUUID(), bd("100"), "ride:" + i, "").await().indefinitely();
        svc.platformMove(tenant, UUID.randomUUID(), bd("-300"), "promo", "").await().indefinitely();
        assertThat(svc.platformBalance(tenant).await().indefinitely()).isEqualByComparingTo("19700");
        assertThat(ledger.size()).isEqualTo(201);
    }

    @Test
    void overdrawnWalletPublishesAnEvent() {
        final var rider = UUID.randomUUID();
        svc.credit(tenant, OwnerKind.RIDER, rider, bd("1000"), "t", "").await().indefinitely();
        svc.capture(tenant, OwnerKind.RIDER, rider, bd("5000"), "none", "cap", "").await().indefinitely();
        assertThat(sink.types()).contains("mobility.wallet.overdrawn");
        assertThat(svc.view(tenant, OwnerKind.RIDER, rider).await().indefinitely().canBook()).isFalse();
    }
}
