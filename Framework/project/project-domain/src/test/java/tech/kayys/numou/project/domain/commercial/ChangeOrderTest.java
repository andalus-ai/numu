package tech.kayys.syirkah.project.domain.commercial;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.event.ChangeOrderApplied;
import tech.kayys.syirkah.project.domain.commercial.event.ChangeOrderApproved;
import tech.kayys.syirkah.project.domain.commercial.event.ChangeOrderCancelled;
import tech.kayys.syirkah.project.domain.commercial.event.ChangeOrderCreated;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.math.BigDecimal;
import java.time.Period;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChangeOrder domain invariants and lifecycle")
class ChangeOrderTest {

    private static final ProjectId PROJECT_ID = ProjectId.generate();
    private static final ProjectContractId CONTRACT_ID = ProjectContractId.generate();

    private ChangeOrder createOrder() {
        var impact = new ChangeImpact(
                Money.of(new BigDecimal("5000.00"), "IDR"),
                Period.ofDays(10),
                true
        );

        return ChangeOrder.create(
                ChangeOrderId.generate(),
                PROJECT_ID,
                CONTRACT_ID,
                null,
                "CO-001",
                "Scope Extension",
                "Add notification service",
                impact
        );
    }

    @Test
    void createsChangeOrderAndRaisesCreatedEvent() {
        var co = createOrder();

        assertEquals(ChangeOrderStatus.DRAFT, co.status());
        assertEquals("CO-001", co.number());
        assertTrue(co.impact().changesPrice());
        assertTrue(co.impact().changesSchedule());

        var events = co.pullDomainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(ChangeOrderCreated.class, events.getFirst());
    }

    @Test
    void approvesAndAppliesChangeOrder() {
        var co = createOrder();
        co.pullDomainEvents();

        co.approve();
        assertEquals(ChangeOrderStatus.APPROVED, co.status());
        assertNotNull(co.approvedAt());
        assertInstanceOf(ChangeOrderApproved.class, co.pullDomainEvents().getFirst());

        co.apply();
        assertEquals(ChangeOrderStatus.APPLIED, co.status());
        assertNotNull(co.appliedAt());
        assertInstanceOf(ChangeOrderApplied.class, co.pullDomainEvents().getFirst());
    }

    @Test
    void cannotApplyWithoutApproval() {
        var co = createOrder();
        assertThrows(InvalidStateException.class, co::apply);
    }

    @Test
    void cancelsDraftOrder() {
        var co = createOrder();
        co.pullDomainEvents();

        co.cancel();
        assertEquals(ChangeOrderStatus.CANCELLED, co.status());
        assertInstanceOf(ChangeOrderCancelled.class, co.pullDomainEvents().getFirst());
    }

    @Test
    void cannotCancelAppliedOrder() {
        var co = createOrder();
        co.approve();
        co.apply();

        assertThrows(InvalidStateException.class, co::cancel);
    }
}
