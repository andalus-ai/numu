package tech.kayys.numou.billing.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.billing.application.api.command.BatchBillingCommand;
import tech.kayys.numou.billing.application.api.command.ProcessBillingCycleCommand;
import tech.kayys.numou.billing.application.api.query.BatchBillingResult;
import tech.kayys.numou.billing.application.api.query.BillingCycleResult;
import tech.kayys.numou.billing.application.port.InvoicePort;
import tech.kayys.numou.billing.application.port.NotificationPort;
import tech.kayys.numou.billing.application.port.PaymentPort;
import tech.kayys.numou.billing.application.service.BillingProcessor;
import tech.kayys.numou.billing.domain.identifier.BillingScheduleId;
import tech.kayys.numou.billing.domain.model.BillingSchedule;
import tech.kayys.numou.billing.domain.repository.BillingScheduleRepository;
import tech.kayys.numou.billing.domain.valueobject.BillingFrequency;
import tech.kayys.numou.billing.domain.valueobject.BillingStatus;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BillingProcessorTest {

    /** Keeps schedules in a map; only what the processor uses is implemented. */
    static final class Repo implements BillingScheduleRepository {
        final Map<BillingScheduleId, BillingSchedule> store = new LinkedHashMap<>();
        int saves;

        public Uni<BillingSchedule> save(BillingSchedule s) {
            saves++;
            store.put(s.getId(), s);
            return Uni.createFrom().item(s);
        }
        public Uni<Optional<BillingSchedule>> findById(BillingScheduleId id) {
            return Uni.createFrom().item(Optional.ofNullable(store.get(id)));
        }
        public Uni<List<BillingSchedule>> findDueSchedules() {
            return Uni.createFrom().item(new ArrayList<>(store.values()));
        }
        public Uni<Boolean> existsById(BillingScheduleId id) { throw new UnsupportedOperationException(); }
        public Uni<Void> delete(BillingSchedule s) { throw new UnsupportedOperationException(); }
        public Uni<Void> deleteById(BillingScheduleId id) { throw new UnsupportedOperationException(); }
        public Uni<Optional<BillingSchedule>> findBySubscriptionId(UUID id) { throw new UnsupportedOperationException(); }
        public Uni<List<BillingSchedule>> findByCustomerId(String c) { throw new UnsupportedOperationException(); }
        public Uni<List<BillingSchedule>> findByStatus(BillingStatus s) { throw new UnsupportedOperationException(); }
        public Uni<List<BillingSchedule>> findSchedulesWithPaymentFailures() { throw new UnsupportedOperationException(); }
        public Uni<List<BillingSchedule>> findUpcomingBilling(int d) { throw new UnsupportedOperationException(); }
        public Uni<List<BillingSchedule>> findExpiredSchedules() { throw new UnsupportedOperationException(); }
        public Uni<BillingSchedule> updateStatus(UUID id, BillingStatus s) { throw new UnsupportedOperationException(); }
    }

    final Repo repo = new Repo();
    final AtomicInteger invoices = new AtomicInteger();
    final AtomicInteger charges = new AtomicInteger();
    final List<String> failInvoiceFor = new ArrayList<>();
    java.util.function.Function<BillingSchedule, PaymentPort.PaymentResult> payment =
            s -> new PaymentPort.PaymentResult(true, "tx-ok", "ok");
    java.util.function.Consumer<BillingSchedule> beforeCharge = s -> { };
    BillingSchedule current;

    final InvoicePort invoicePort = new InvoicePort() {
        public CompletionStage<String> generateInvoice(String customerId, Money amount, String cur, String d) {
            if (failInvoiceFor.contains(customerId)) {
                return CompletableFuture.failedFuture(new IllegalStateException("invoice service down"));
            }
            return CompletableFuture.completedFuture("inv-" + invoices.incrementAndGet());
        }
        public CompletionStage<InvoiceDetails> getInvoice(String id) { throw new UnsupportedOperationException(); }
    };

    BillingSchedule byToken(String token) {
        return repo.store.values().stream().filter(s -> token.equals(s.getPaymentMethodToken())).findFirst()
                .orElseThrow();
    }

    final PaymentPort paymentPort = new PaymentPort() {
        public CompletionStage<PaymentResult> processPayment(String token, Money amount, String cur) {
            charges.incrementAndGet();
            BillingSchedule s = byToken(token);
            beforeCharge.accept(s);
            try {
                return CompletableFuture.completedFuture(payment.apply(s));
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        public CompletionStage<PaymentResult> refundPayment(String t, Money a, String c) {
            throw new UnsupportedOperationException();
        }
    };

    final NotificationPort notifications = (NotificationPort) java.lang.reflect.Proxy.newProxyInstance(
            NotificationPort.class.getClassLoader(), new Class<?>[]{NotificationPort.class},
            (proxy, m, a) -> m.getReturnType() == CompletionStage.class
                    ? CompletableFuture.completedFuture(null) : null);

    final BillingProcessor processor = new BillingProcessor(repo, invoicePort, paymentPort, notifications);

    /** An active schedule that fell due about two months ago. */
    BillingSchedule due(String customer, String currency, String token) {
        BillingSchedule s = BillingSchedule.create(BillingScheduleId.generate(), UUID.randomUUID(), customer,
                BillingFrequency.MONTHLY, Money.of(100L, currency), currency, Instant.now().minus(Duration.ofDays(90)));
        s.setPaymentMethodToken(token);
        s.activate(Instant.now().minus(Duration.ofDays(90)));
        repo.store.put(s.getId(), s);
        return s;
    }

    static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    @Test
    void aSuccessfulCycleIsInvoicedChargedAndRecorded() {
        BillingSchedule s = due("c1", "USD", "tok1");
        BillingCycleResult r = join(processor.processBillingCycle(new ProcessBillingCycleCommand(s.getId())));
        assertTrue(r.success());
        assertEquals("tx-ok", r.transactionId());
        assertEquals(1, invoices.get());
        assertEquals(1, charges.get());
        assertEquals(1, s.getBillingCycles().size());
        assertEquals(BillingSchedule.BillingCycleStatus.SUCCESS, s.getBillingCycles().get(0).getStatus());
    }

    @Test
    void theCycleIsStoredAsPendingBeforeMoneyIsTaken() {
        BillingSchedule s = due("c1", "USD", "tok1");
        List<String> seen = new ArrayList<>();
        beforeCharge = sched -> seen.add(sched.getBillingCycles().get(0).getStatus() + "/" + repo.saves);
        join(processor.processBillingCycle(new ProcessBillingCycleCommand(s.getId())));
        assertEquals(List.of("PENDING/1"), seen, "one save, with the cycle pending, happened before the charge");
        assertEquals(2, repo.saves);
    }

    @Test
    void aScheduleThatCannotBeBilledGetsNoInvoiceAndNoCharge() {
        BillingSchedule s = due("c1", "USD", "tok1");
        s.pause();
        CompletionException e = assertThrows(CompletionException.class,
                () -> join(processor.processBillingCycle(new ProcessBillingCycleCommand(s.getId()))));
        assertTrue(e.getCause() instanceof IllegalStateException);
        assertEquals(0, invoices.get());
        assertEquals(0, charges.get());
    }

    @Test
    void aPaymentThatThrowsIsAFailedPaymentNotALostOne() {
        BillingSchedule s = due("c1", "USD", "tok1");
        payment = x -> { throw new IllegalStateException("gateway timeout"); };
        BillingCycleResult r = join(processor.processBillingCycle(new ProcessBillingCycleCommand(s.getId())));
        assertFalse(r.success());
        assertTrue(r.message().contains("gateway timeout"), r.message());
        assertEquals(BillingSchedule.BillingCycleStatus.FAILED, s.getBillingCycles().get(0).getStatus());
        assertEquals(1, s.getFailedPaymentCount());
    }

    @Test
    void oneBrokenScheduleDoesNotStopTheBatch() {
        due("good", "USD", "tok-good");
        due("bad", "USD", "tok-bad");
        failInvoiceFor.add("bad");
        BatchBillingResult r = join(processor.processDueBillings(new BatchBillingCommand("b1", Instant.now())));
        assertEquals(2, r.totalProcessed());
        assertEquals(1, r.successful());
        assertEquals(1, r.failed());
        assertEquals(0, new java.math.BigDecimal("100").compareTo(r.totalAmount().getAmount()));
    }

    @Test
    void aBatchInSeveralCurrenciesDoesNotAddThemTogether() {
        due("c1", "USD", "tok1");
        due("c2", "EUR", "tok2");
        BatchBillingResult r = join(processor.processDueBillings(new BatchBillingCommand("b2", Instant.now())));
        assertEquals(2, r.successful());
        assertTrue(r.message().contains("several currencies"), r.message());
    }

    @Test
    void aBatchOfEuroSchedulesIsTotalledInEuro() {
        due("c1", "EUR", "tok1");
        due("c2", "EUR", "tok2");
        BatchBillingResult r = join(processor.processDueBillings(new BatchBillingCommand("b3", Instant.now())));
        assertEquals("EUR", r.totalAmount().getCurrency().getCurrencyCode());
        assertEquals(0, new java.math.BigDecimal("200").compareTo(r.totalAmount().getAmount()));
    }
}
