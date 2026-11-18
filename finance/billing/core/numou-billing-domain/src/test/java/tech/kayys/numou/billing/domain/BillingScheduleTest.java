package tech.kayys.numou.billing.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.billing.domain.identifier.BillingScheduleId;
import tech.kayys.numou.billing.domain.model.BillingSchedule;
import tech.kayys.numou.billing.domain.valueobject.BillingFrequency;
import tech.kayys.numou.billing.domain.valueobject.BillingStatus;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BillingScheduleTest {

    static Instant at(String iso) {
        return Instant.parse(iso);
    }

    static BillingSchedule active(BillingFrequency f, String start) {
        BillingSchedule s = BillingSchedule.create(BillingScheduleId.generate(), UUID.randomUUID(), "cust", f,
                Money.of(100L, "USD"), "USD", at(start));
        s.activate(at(start));
        return s;
    }

    @Test
    void monthEndKeepsItsAnchorDayInsteadOfDrifting() {
        BillingSchedule s = active(BillingFrequency.MONTHLY, "2026-01-31T00:00:00Z");
        assertEquals(at("2026-02-28T00:00:00Z"), s.getNextBillingDate());
        s.processBillingCycle(at("2026-02-28T00:00:00Z"), "inv-1");
        assertEquals(at("2026-03-31T00:00:00Z"), s.getNextBillingDate());
        s.processBillingCycle(at("2026-03-31T00:00:00Z"), "inv-2");
        assertEquals(at("2026-04-30T00:00:00Z"), s.getNextBillingDate());
    }

    @Test
    void aLateRunDoesNotShiftTheSchedule() {
        BillingSchedule s = active(BillingFrequency.MONTHLY, "2026-01-15T00:00:00Z");
        assertEquals(at("2026-02-15T00:00:00Z"), s.getNextBillingDate());
        s.processBillingCycle(at("2026-02-20T09:30:00Z"), "inv-1");
        assertEquals(at("2026-03-15T00:00:00Z"), s.getNextBillingDate());
    }

    @Test
    void nothingIsBilledBeforeTheDueDateOrWhileNotActive() {
        BillingSchedule s = active(BillingFrequency.MONTHLY, "2026-01-15T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> s.ensureBillable(at("2026-02-14T23:59:59Z")));
        s.ensureBillable(at("2026-02-15T00:00:00Z"));
        s.pause();
        assertThrows(IllegalStateException.class, () -> s.ensureBillable(at("2026-03-01T00:00:00Z")));
    }

    @Test
    void theScheduleCompletesOnlyWhenTheLastPaymentSucceeds() {
        BillingSchedule s = active(BillingFrequency.MONTHLY, "2026-01-15T00:00:00Z");
        s.setTotalCycles(1);
        BillingSchedule.BillingCycle c = s.processBillingCycle(at("2026-02-15T00:00:00Z"), "inv-1");
        assertEquals(BillingStatus.ACTIVE, s.getStatus(), "payment is not known yet");
        assertThrows(IllegalStateException.class, () -> s.ensureBillable(at("2026-03-20T00:00:00Z")),
                "the agreed number of cycles has been billed");
        s.markCycleSuccess(c.getCycleNumber(), "tx-1");
        assertEquals(BillingStatus.COMPLETED, s.getStatus());
    }

    @Test
    void aFailedLastPaymentDoesNotCompleteTheSchedule() {
        BillingSchedule s = active(BillingFrequency.MONTHLY, "2026-01-15T00:00:00Z");
        s.setTotalCycles(1);
        BillingSchedule.BillingCycle c = s.processBillingCycle(at("2026-02-15T00:00:00Z"), "inv-1");
        s.markCycleFailed(c.getCycleNumber(), "declined");
        assertNotEquals(BillingStatus.COMPLETED, s.getStatus());
        assertEquals(1, s.getFailedPaymentCount());
    }

    @Test
    void tooManyFailedPaymentsStopTheSchedule() {
        BillingSchedule s = active(BillingFrequency.DAILY, "2026-01-01T00:00:00Z");
        s.setMaxFailedPayments(2);
        s.processBillingCycle(at("2026-01-02T00:00:00Z"), "inv-1");
        s.markCycleFailed(1, "declined");
        assertEquals(BillingStatus.ACTIVE, s.getStatus());
        s.processBillingCycle(at("2026-01-03T00:00:00Z"), "inv-2");
        s.markCycleFailed(2, "declined");
        assertEquals(BillingStatus.FAILED, s.getStatus());
    }

    @Test
    void aLoadedScheduleKeepsItsProgressAndIsNotBilledAgain() {
        BillingSchedule s = BillingSchedule.create(BillingScheduleId.generate(), UUID.randomUUID(), "cust",
                BillingFrequency.MONTHLY, Money.of(100L, "USD"), "USD", at("2026-01-15T00:00:00Z"));
        s.setStatus(BillingStatus.ACTIVE);
        s.restoreProgress(at("2026-04-15T00:00:00Z"), at("2026-03-15T00:00:00Z"), 2, 1);
        assertEquals(2, s.getCurrentCycle());
        assertEquals(1, s.getFailedPaymentCount());
        assertEquals(at("2026-04-15T00:00:00Z"), s.getNextBillingDate());
        assertThrows(IllegalArgumentException.class, () -> s.ensureBillable(at("2026-03-20T00:00:00Z")));
    }

    @Test
    void aFailedCycleCanBeRetriedOnceAndOnlyWhileTheScheduleIsActive() {
        BillingSchedule s = active(BillingFrequency.MONTHLY, "2026-01-15T00:00:00Z");
        assertThrows(IllegalStateException.class, () -> s.beginRetry(null), "nothing has failed yet");
        BillingSchedule.BillingCycle c = s.processBillingCycle(at("2026-02-15T00:00:00Z"), "inv-1");
        assertThrows(IllegalStateException.class, () -> s.beginRetry(null), "a pending cycle is not a failed one");
        s.markCycleFailed(c.getCycleNumber(), "declined");
        BillingSchedule.BillingCycle again = s.beginRetry(null);
        assertSame(c, again);
        assertEquals(BillingSchedule.BillingCycleStatus.RETRY, c.getStatus());
        assertEquals("inv-1", c.getInvoiceId(), "the retry uses the invoice it already has");
        assertThrows(IllegalStateException.class, () -> s.beginRetry(null), "already being retried");
        s.markCycleSuccess(c.getCycleNumber(), "tx-2");
        assertEquals(BillingSchedule.BillingCycleStatus.SUCCESS, c.getStatus());
        assertEquals(0, s.getFailedPaymentCount());
    }

    @Test
    void aStoppedScheduleIsNotRetried() {
        BillingSchedule s = active(BillingFrequency.MONTHLY, "2026-01-15T00:00:00Z");
        s.setMaxFailedPayments(1);
        s.processBillingCycle(at("2026-02-15T00:00:00Z"), "inv-1");
        s.markCycleFailed(1, "declined");
        assertEquals(BillingStatus.FAILED, s.getStatus());
        assertThrows(IllegalStateException.class, () -> s.beginRetry(null));
    }

    @Test
    void storedCyclesComeBackInOrderWithTheirOutcome() {
        BillingSchedule s = active(BillingFrequency.MONTHLY, "2026-01-15T00:00:00Z");
        BillingSchedule.BillingCycle two = BillingSchedule.BillingCycle.restore(2, at("2026-03-15T00:00:00Z"),
                Money.of(100L, "USD"), "USD", "inv-2", BillingSchedule.BillingCycleStatus.FAILED, null, "declined",
                at("2026-03-15T00:00:01Z"), at("2026-03-15T00:00:02Z"));
        BillingSchedule.BillingCycle one = BillingSchedule.BillingCycle.restore(1, at("2026-02-15T00:00:00Z"),
                Money.of(100L, "USD"), "USD", "inv-1", BillingSchedule.BillingCycleStatus.SUCCESS, "tx-1", null,
                at("2026-02-15T00:00:01Z"), at("2026-02-15T00:00:02Z"));
        s.restoreCycles(java.util.List.of(two, one));
        assertEquals(java.util.List.of(1, 2), s.getBillingCycles().stream().map(c -> c.getCycleNumber()).toList());
        s.restoreProgress(at("2026-04-15T00:00:00Z"), at("2026-03-15T00:00:00Z"), 2, 1);
        assertSame(two, s.beginRetry(null));
    }
}
