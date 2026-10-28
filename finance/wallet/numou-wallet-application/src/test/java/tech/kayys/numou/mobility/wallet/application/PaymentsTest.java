package tech.kayys.numou.wallet.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.aggregate.memory.RecordingEventSink;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.wallet.adapter.memory.InMemoryPaymentRepositories;
import tech.kayys.numou.wallet.adapter.memory.InMemoryWalletRepositories;
import tech.kayys.numou.wallet.application.TopUpService.Outcome;
import tech.kayys.numou.wallet.application.payment.*;
import tech.kayys.numou.wallet.domain.OrderIds;
import tech.kayys.numou.wallet.domain.OwnerKind;
import tech.kayys.numou.wallet.domain.Payout;
import tech.kayys.numou.wallet.spi.PayoutProvider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentsTest {

    static BigDecimal bd(String s) { return new BigDecimal(s); }
    static final String SERVER_KEY = "SB-Mid-server-key-for-tests";
    static final String XENDIT_TOKEN = "xendit-callback-token-for-tests";
    static final String HMAC_SECRET = "generic-hmac-secret-0123456789";

    final UUID tenant = UUID.randomUUID();
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-10T10:00:00Z"));
    final UnitOfWork uow = new UnitOfWork() { public <R> Uni<R> execute(java.util.function.Supplier<Uni<R>> w) { return w.get(); } };
    final RecordingEventSink sink = new RecordingEventSink();
    WalletService wallets;
    TopUpService topUps;
    PayoutService payouts;
    final List<String> chargesCreated = new ArrayList<>();
    final List<UUID> disbursed = new CopyOnWriteArrayList<>();
    final AtomicInteger disburseFailures = new AtomicInteger();

    @BeforeEach
    void setUp() {
        wallets = new WalletService(new InMemoryWalletRepositories.Wallets(), new InMemoryWalletRepositories.Ledger(), uow, sink, now::get, "IDR", 0, bd("100000"), bd("50000"));
        final Map<String, PaymentProvider> providers = Map.of(
                "midtrans", new MidtransProvider(SERVER_KEY), "xendit", new XenditProvider(XENDIT_TOKEN),
                "partner", new GenericHmacProvider("partner", HMAC_SECRET, Duration.ofMinutes(5)));
        topUps = new TopUpService(new InMemoryPaymentRepositories.TopUps(), wallets,
                (provider, orderId, amount, currency, rider, ttl) -> { chargesCreated.add(orderId); return Uni.createFrom().item("https://pay.example/" + orderId); },
                providers, uow, sink, now::get, bd("10000"), bd("5000000"), Duration.ofHours(1), "IDR");
        final PayoutProvider bank = new PayoutProvider() {
            public Uni<String> disburse(UUID t, Payout p) {
                if (disburseFailures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) return Uni.createFrom().failure(new IllegalStateException("bank API down"));
                disbursed.add(p.id().value()); return Uni.createFrom().item("bank-ref-" + p.id());
            }
            public Result parseResult(Map<String, String> h, String body, Instant at) { throw new UnsupportedOperationException(); }
        };
        payouts = new PayoutService(new InMemoryPaymentRepositories.Payouts(), wallets, bank, uow, sink, now::get, "IDR");
    }

    // ---------------------------------------------------------------- independent signature helpers (separate code path from the provider classes)

    static String hex(byte[] b) { return HexFormat.of().formatHex(b); }

    static String sha512(String s) throws Exception { return hex(MessageDigest.getInstance("SHA-512").digest(s.getBytes(StandardCharsets.UTF_8))); }

    static String hmac(String secret, String data) throws Exception {
        final var m = Mac.getInstance("HmacSHA256"); m.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return hex(m.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    static String midtransBody(String orderId, String status, String gross, String tx, String fraud, String key) throws Exception {
        final String sig = sha512(orderId + "200" + gross + key);
        return "{\"order_id\":\"" + orderId + "\",\"status_code\":\"200\",\"gross_amount\":\"" + gross + "\",\"transaction_status\":\"" + tx
                + "\",\"fraud_status\":\"" + fraud + "\",\"transaction_id\":\"mt-1\",\"signature_key\":\"" + sig + "\"}";
    }

    String newIntent(UUID rider, String amount, String provider) {
        return topUps.create(tenant, rider, bd(amount), provider).await().indefinitely().orderId();
    }

    // ---------------------------------------------------------------- order ids

    @Test
    void orderIdsRoundTripFitGatewayLimitsAndRejectGarbage() {
        final UUID t = UUID.randomUUID(), i = UUID.randomUUID();
        final String id = OrderIds.topUp(t, i);
        assertThat(id).hasSize(46).matches("TU[A-Za-z0-9_-]{44}");
        assertThat(OrderIds.decodeTopUp(id)).contains(new OrderIds.Ids(t, i));
        assertThat(OrderIds.decodePayout(id)).isEmpty();                                   // a top-up id is not a payout id
        assertThat(OrderIds.decodePayout(OrderIds.payout(t, i))).contains(new OrderIds.Ids(t, i));
        for (final String bad : new String[]{null, "", "TU", "XX" + id.substring(2), id.substring(0, 45), id + "A", "TU!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"}) {
            assertThat(OrderIds.decodeTopUp(bad)).as("%s", bad).isEmpty();
        }
    }

    // ---------------------------------------------------------------- providers

    @Test
    void midtransAcceptsAValidSignatureAndMapsStatuses() throws Exception {
        final var p = new MidtransProvider(SERVER_KEY);
        final var n = p.parse(Map.of(), midtransBody("ORDER1", "x", "29000.00", "settlement", "accept", SERVER_KEY), now.get());
        assertThat(n.status()).isEqualTo(PaymentNotification.Status.SETTLED);
        assertThat(n.amount()).isEqualByComparingTo("29000");
        assertThat(n.orderId()).isEqualTo("ORDER1");
        assertThat(p.parse(Map.of(), midtransBody("O", "x", "1.00", "capture", "accept", SERVER_KEY), now.get()).status()).isEqualTo(PaymentNotification.Status.SETTLED);
        assertThat(p.parse(Map.of(), midtransBody("O", "x", "1.00", "capture", "challenge", SERVER_KEY), now.get()).status()).isEqualTo(PaymentNotification.Status.PENDING);
        assertThat(p.parse(Map.of(), midtransBody("O", "x", "1.00", "pending", "", SERVER_KEY), now.get()).status()).isEqualTo(PaymentNotification.Status.PENDING);
        assertThat(p.parse(Map.of(), midtransBody("O", "x", "1.00", "deny", "", SERVER_KEY), now.get()).status()).isEqualTo(PaymentNotification.Status.FAILED);
        assertThat(p.parse(Map.of(), midtransBody("O", "x", "1.00", "expire", "", SERVER_KEY), now.get()).status()).isEqualTo(PaymentNotification.Status.EXPIRED);
    }

    @Test
    void midtransRejectsWrongKeyTamperedAmountAndGarbage() throws Exception {
        final var p = new MidtransProvider(SERVER_KEY);
        final String signedForOther = midtransBody("ORDER1", "x", "29000.00", "settlement", "accept", "someone-elses-key");
        assertThatThrownBy(() -> p.parse(Map.of(), signedForOther, now.get())).isInstanceOf(InvalidSignatureException.class);
        final String good = midtransBody("ORDER1", "x", "29000.00", "settlement", "accept", SERVER_KEY);
        assertThatThrownBy(() -> p.parse(Map.of(), good.replace("29000.00", "99000.00"), now.get())).isInstanceOf(InvalidSignatureException.class);   // amount changed after signing
        assertThatThrownBy(() -> p.parse(Map.of(), good.replace("ORDER1", "ORDER2"), now.get())).isInstanceOf(InvalidSignatureException.class);
        assertThatThrownBy(() -> p.parse(Map.of(), "not json", now.get())).isInstanceOf(InvalidSignatureException.class);
        assertThatThrownBy(() -> p.parse(Map.of(), "{}", now.get())).isInstanceOf(InvalidSignatureException.class);
    }

    @Test
    void xenditChecksTheCallbackTokenInConstantTime() {
        final var p = new XenditProvider(XENDIT_TOKEN);
        final String body = "{\"external_id\":\"O1\",\"status\":\"PAID\",\"paid_amount\":50000,\"amount\":50000,\"id\":\"inv-1\"}";
        final var n = p.parse(Map.of("x-callback-token", XENDIT_TOKEN), body, now.get());
        assertThat(n.status()).isEqualTo(PaymentNotification.Status.SETTLED);
        assertThat(n.amount()).isEqualByComparingTo("50000");
        assertThatThrownBy(() -> p.parse(Map.of("x-callback-token", "wrong"), body, now.get())).isInstanceOf(InvalidSignatureException.class);
        assertThatThrownBy(() -> p.parse(Map.of(), body, now.get())).isInstanceOf(InvalidSignatureException.class);
        assertThat(p.parse(Map.of("x-callback-token", XENDIT_TOKEN), body.replace("PAID", "EXPIRED"), now.get()).status()).isEqualTo(PaymentNotification.Status.EXPIRED);
    }

    @Test
    void genericHmacRejectsTamperingAndReplays() throws Exception {
        final var p = new GenericHmacProvider("partner", HMAC_SECRET, Duration.ofMinutes(5));
        final String body = "{\"orderId\":\"O1\",\"amount\":\"20000\",\"status\":\"SETTLED\",\"providerRef\":\"r1\"}";
        final String ts = Long.toString(now.get().getEpochSecond());
        final Map<String, String> ok = Map.of("x-timestamp", ts, "x-signature", hmac(HMAC_SECRET, ts + "." + body));
        assertThat(p.parse(ok, body, now.get()).status()).isEqualTo(PaymentNotification.Status.SETTLED);
        assertThatThrownBy(() -> p.parse(ok, body.replace("20000", "90000"), now.get())).isInstanceOf(InvalidSignatureException.class);
        assertThatThrownBy(() -> p.parse(ok, body, now.get().plusSeconds(600))).isInstanceOf(InvalidSignatureException.class);   // a captured callback replayed later
        assertThatThrownBy(() -> p.parse(Map.of(), body, now.get())).isInstanceOf(InvalidSignatureException.class);
        assertThatThrownBy(() -> p.parse(Map.of("x-timestamp", "abc", "x-signature", "00"), body, now.get())).isInstanceOf(InvalidSignatureException.class);
    }

    // ---------------------------------------------------------------- top-ups

    @Test
    void aSettledCallbackCreditsTheRiderOnceEvenWhenDeliveredManyTimes() throws Exception {
        final UUID rider = UUID.randomUUID();
        final String order = newIntent(rider, "50000", "midtrans");
        assertThat(chargesCreated).containsExactly(order);
        final String body = midtransBody(order, "x", "50000.00", "settlement", "accept", SERVER_KEY);
        assertThat(topUps.onNotification("midtrans", Map.of(), body).await().indefinitely()).isEqualTo(Outcome.CREDITED);
        assertThat(topUps.onNotification("midtrans", Map.of(), body).await().indefinitely()).isEqualTo(Outcome.DUPLICATE);
        assertThat(wallets.view(tenant, OwnerKind.RIDER, rider).await().indefinitely().balance()).isEqualByComparingTo("50000");
        assertThat(sink.types()).contains("mobility.wallet.topup-paid");
    }

    @Test
    void concurrentRedeliveriesStillCreditExactlyOnce() throws Exception {
        for (int round = 0; round < 10; round++) {
            setUp();
            final UUID rider = UUID.randomUUID();
            final String order = newIntent(rider, "75000", "midtrans");
            final String body = midtransBody(order, "x", "75000.00", "settlement", "accept", SERVER_KEY);
            final var pool = Executors.newFixedThreadPool(6); final var go = new CountDownLatch(1);
            final List<Future<Outcome>> fs = new ArrayList<>();
            for (int i = 0; i < 6; i++) fs.add(pool.submit(() -> { go.await(); return topUps.onNotification("midtrans", Map.of(), body).await().indefinitely(); }));
            go.countDown();
            int credited = 0;
            for (var f : fs) if (f.get(10, TimeUnit.SECONDS) == Outcome.CREDITED) credited++;
            pool.shutdown();
            assertThat(credited).as("round %d: callbacks that credited", round).isEqualTo(1);
            assertThat(wallets.view(tenant, OwnerKind.RIDER, rider).await().indefinitely().balance()).as("round %d", round).isEqualByComparingTo("75000");
        }
    }

    @Test
    void aForgedCallbackNeverCreditsAnything() throws Exception {
        final UUID rider = UUID.randomUUID();
        final String order = newIntent(rider, "50000", "midtrans");
        final String forged = midtransBody(order, "x", "50000.00", "settlement", "accept", "attacker-guess");
        assertThatThrownBy(() -> topUps.onNotification("midtrans", Map.of(), forged).await().indefinitely()).isInstanceOf(InvalidSignatureException.class);
        assertThat(wallets.view(tenant, OwnerKind.RIDER, rider).await().indefinitely().balance()).isEqualByComparingTo("0");
    }

    @Test
    void anAmountMismatchIsParkedForOpsAndNeverCredited() throws Exception {
        final UUID rider = UUID.randomUUID();
        final String order = newIntent(rider, "50000", "midtrans");
        final String underpaid = midtransBody(order, "x", "5000.00", "settlement", "accept", SERVER_KEY);       // genuinely signed, but for the wrong amount
        assertThat(topUps.onNotification("midtrans", Map.of(), underpaid).await().indefinitely()).isEqualTo(Outcome.MISMATCH);
        assertThat(wallets.view(tenant, OwnerKind.RIDER, rider).await().indefinitely().balance()).isEqualByComparingTo("0");
        assertThat(sink.types()).contains("mobility.wallet.topup-mismatch");
        final String correct = midtransBody(order, "x", "50000.00", "settlement", "accept", SERVER_KEY);
        assertThat(topUps.onNotification("midtrans", Map.of(), correct).await().indefinitely()).isEqualTo(Outcome.CREDITED);   // the real payment still works
    }

    @Test
    void paymentArrivingAfterTheIntentExpiredIsStillCredited() throws Exception {
        final UUID rider = UUID.randomUUID();
        final String order = newIntent(rider, "50000", "xendit");
        now.set(now.get().plus(Duration.ofHours(3)));
        final String body = "{\"external_id\":\"" + order + "\",\"status\":\"PAID\",\"paid_amount\":50000,\"id\":\"inv-9\"}";
        assertThat(topUps.onNotification("xendit", Map.of("x-callback-token", XENDIT_TOKEN), body).await().indefinitely()).isEqualTo(Outcome.CREDITED);
        assertThat(wallets.view(tenant, OwnerKind.RIDER, rider).await().indefinitely().balance()).isEqualByComparingTo("50000");   // the rider paid: money is money
    }

    @Test
    void pendingFailedUnknownAndForeignNotificationsCreditNothing() throws Exception {
        final UUID rider = UUID.randomUUID();
        final String order = newIntent(rider, "50000", "midtrans");
        assertThat(topUps.onNotification("midtrans", Map.of(), midtransBody(order, "x", "50000.00", "pending", "", SERVER_KEY)).await().indefinitely()).isEqualTo(Outcome.PENDING);
        assertThat(topUps.onNotification("midtrans", Map.of(), midtransBody(order, "x", "50000.00", "deny", "", SERVER_KEY)).await().indefinitely()).isEqualTo(Outcome.FAILED);
        final String unknownOrder = OrderIds.topUp(tenant, UUID.randomUUID());
        assertThat(topUps.onNotification("midtrans", Map.of(), midtransBody(unknownOrder, "x", "50000.00", "settlement", "accept", SERVER_KEY)).await().indefinitely()).isEqualTo(Outcome.IGNORED);
        assertThat(topUps.onNotification("midtrans", Map.of(), midtransBody("not-our-order-id", "x", "50000.00", "settlement", "accept", SERVER_KEY)).await().indefinitely()).isEqualTo(Outcome.IGNORED);
        assertThat(topUps.onNotification("unknown-provider", Map.of(), "{}").await().indefinitely()).isEqualTo(Outcome.IGNORED);
        final String otherTenantsOrder = OrderIds.topUp(UUID.randomUUID(), UUID.randomUUID());
        assertThat(topUps.onNotification("midtrans", Map.of(), midtransBody(otherTenantsOrder, "x", "50000.00", "settlement", "accept", SERVER_KEY)).await().indefinitely()).isEqualTo(Outcome.IGNORED);
        assertThat(wallets.view(tenant, OwnerKind.RIDER, rider).await().indefinitely().balance()).isEqualByComparingTo("0");
    }

    @Test
    void aSettledCallbackCannotBeUsedToCreditAnotherRidersIntent() throws Exception {
        final UUID alice = UUID.randomUUID(), mallory = UUID.randomUUID();
        final String aliceOrder = newIntent(alice, "50000", "midtrans");
        newIntent(mallory, "50000", "midtrans");
        topUps.onNotification("midtrans", Map.of(), midtransBody(aliceOrder, "x", "50000.00", "settlement", "accept", SERVER_KEY)).await().indefinitely();
        assertThat(wallets.view(tenant, OwnerKind.RIDER, alice).await().indefinitely().balance()).isEqualByComparingTo("50000");
        assertThat(wallets.view(tenant, OwnerKind.RIDER, mallory).await().indefinitely().balance()).isEqualByComparingTo("0");
    }

    @Test
    void topUpLimitsAndProvidersAreValidated() {
        final UUID r = UUID.randomUUID();
        assertThatThrownBy(() -> topUps.create(tenant, r, bd("9999"), "midtrans").await().indefinitely()).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> topUps.create(tenant, r, bd("5000001"), "midtrans").await().indefinitely()).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> topUps.create(tenant, r, bd("20000"), "nope").await().indefinitely()).hasMessageContaining("Unknown payment provider");
        assertThat(topUps.create(tenant, r, bd("10000"), "partner").await().indefinitely().paymentUrl()).startsWith("https://pay.example/");
    }

    // ---------------------------------------------------------------- payouts

    UUID fundedDriver(String amount) {
        final UUID d = UUID.randomUUID();
        wallets.credit(tenant, OwnerKind.DRIVER, d, bd(amount), "fixture:" + d, "").await().indefinitely();
        return d;
    }

    @Test
    void aPayoutDebitsImmediatelyAndIsIdempotentOnTheKey() {
        final UUID d = fundedDriver("200000");
        final var first = payouts.request(tenant, d, bd("120000"), "bank-token-1", "key-1").await().indefinitely();
        final var again = payouts.request(tenant, d, bd("120000"), "bank-token-1", "key-1").await().indefinitely();     // the app retried after a timeout
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(wallets.view(tenant, OwnerKind.DRIVER, d).await().indefinitely().balance()).isEqualByComparingTo("80000");
        final var other = payouts.request(tenant, d, bd("60000"), "bank-token-1", "key-2").await().indefinitely();     // a different key is a different withdrawal
        assertThat(other.id()).isNotEqualTo(first.id());
        assertThat(wallets.view(tenant, OwnerKind.DRIVER, d).await().indefinitely().balance()).isEqualByComparingTo("20000");
    }

    @Test
    void payoutsAreRefusedWithoutFundsOrBelowTheMinimumAndCreateNothing() {
        final UUID d = fundedDriver("60000");
        assertThatThrownBy(() -> payouts.request(tenant, d, bd("70000"), "bank-token", "k1").await().indefinitely()).isInstanceOf(tech.kayys.numou.wallet.domain.InsufficientFundsException.class);
        assertThatThrownBy(() -> payouts.request(tenant, d, bd("40000"), "bank-token", "k2").await().indefinitely()).hasMessageContaining("Minimum payout");
        assertThatThrownBy(() -> payouts.request(tenant, d, bd("60000"), " ", "k3").await().indefinitely()).isInstanceOf(IllegalArgumentException.class);
        assertThat(wallets.view(tenant, OwnerKind.DRIVER, d).await().indefinitely().balance()).isEqualByComparingTo("60000");
    }

    @Test
    void theSweeperSubmitsRequestedPayoutsAndRetriesAfterProviderFailures() {
        final UUID d = fundedDriver("300000");
        payouts.request(tenant, d, bd("100000"), "bank-token", "a").await().indefinitely();
        payouts.request(tenant, d, bd("100000"), "bank-token", "b").await().indefinitely();
        disburseFailures.set(2);                                                        // the bank API is down for the first two attempts
        assertThat(payouts.submitDue(tenant, 10).await().indefinitely()).isZero();
        assertThat(payouts.submitDue(tenant, 10).await().indefinitely()).isEqualTo(2);
        assertThat(disbursed).hasSize(2);
        assertThat(payouts.submitDue(tenant, 10).await().indefinitely()).isZero();      // nothing left to submit
    }

    @Test
    void aFailedTransferReturnsTheMoneyExactlyOnceEvenIfTheCallbackRepeats() {
        final UUID d = fundedDriver("200000");
        final var p = payouts.request(tenant, d, bd("150000"), "bank-token", "k").await().indefinitely();
        payouts.submitDue(tenant, 10).await().indefinitely();
        assertThat(wallets.view(tenant, OwnerKind.DRIVER, d).await().indefinitely().balance()).isEqualByComparingTo("50000");
        final var failed = new PayoutProvider.Result(tenant, p.id(), false, "account closed");
        payouts.onResult(failed).await().indefinitely();
        payouts.onResult(failed).await().indefinitely();                                // the provider redelivered
        payouts.onResult(failed).await().indefinitely();
        assertThat(wallets.view(tenant, OwnerKind.DRIVER, d).await().indefinitely().balance()).isEqualByComparingTo("200000");   // refunded once, not three times
        assertThat(sink.types().stream().filter(t -> t.equals("mobility.wallet.payout-failed"))).hasSize(1);
    }

    @Test
    void aPaidPayoutStaysPaidAndALateFailureIsRefusedForAHuman() {
        final UUID d = fundedDriver("200000");
        final var p = payouts.request(tenant, d, bd("100000"), "bank-token", "k").await().indefinitely();
        payouts.submitDue(tenant, 10).await().indefinitely();
        payouts.onResult(new PayoutProvider.Result(tenant, p.id(), true, "")).await().indefinitely();
        payouts.onResult(new PayoutProvider.Result(tenant, p.id(), true, "")).await().indefinitely();                              // idempotent
        assertThatThrownBy(() -> payouts.onResult(new PayoutProvider.Result(tenant, p.id(), false, "bounced")).await().indefinitely())
                .isInstanceOf(InvalidStateException.class);                                                                        // never auto-refund money that was sent
        assertThat(wallets.view(tenant, OwnerKind.DRIVER, d).await().indefinitely().balance()).isEqualByComparingTo("100000");
    }

    @Test
    void aFailureReportedAfterAPaidResultOrForUnknownPayoutsIsRejected() {
        assertThatThrownBy(() -> payouts.onResult(new PayoutProvider.Result(tenant, UUID.randomUUID(), true, "")).await().indefinitely())
                .isInstanceOf(tech.kayys.numou.aggregate.domain.NotFoundException.class);
    }

    @Test
    void concurrentSameKeyPayoutRequestsDebitOnce() throws Exception {
        for (int round = 0; round < 10; round++) {
            setUp();
            final UUID d = fundedDriver("300000");
            final var pool = Executors.newFixedThreadPool(6); final var go = new CountDownLatch(1);
            final List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < 6; i++) fs.add(pool.submit(() -> { go.await(); payouts.request(tenant, d, bd("100000"), "bank-token", "same-key").await().indefinitely(); return null; }));
            go.countDown();
            for (var f : fs) f.get(10, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(wallets.view(tenant, OwnerKind.DRIVER, d).await().indefinitely().balance()).as("round %d", round).isEqualByComparingTo("200000");
        }
    }
}
