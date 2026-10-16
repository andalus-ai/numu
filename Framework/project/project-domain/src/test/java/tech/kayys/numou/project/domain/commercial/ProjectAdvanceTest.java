package tech.kayys.syirkah.project.domain.commercial;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.event.AdvanceApplied;
import tech.kayys.syirkah.project.domain.commercial.event.AdvanceCreated;
import tech.kayys.syirkah.project.domain.commercial.event.AdvanceReceived;
import tech.kayys.syirkah.project.domain.commercial.event.AdvanceRefunded;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProjectAdvance domain invariants and lifecycle")
class ProjectAdvanceTest {

    private static final ProjectId PROJECT_ID = ProjectId.generate();
    private static final ProjectContractId CONTRACT_ID = ProjectContractId.generate();
    private static final Money ADVANCE = Money.of(new BigDecimal("20000.00"), "IDR");

    private ProjectAdvance createAdvance() {
        return ProjectAdvance.create(
                ProjectAdvanceId.generate(),
                PROJECT_ID,
                CONTRACT_ID,
                ADVANCE
        );
    }

    @Test
    void createsAdvanceAndRaisesEvent() {
        var advance = createAdvance();

        assertEquals(AdvanceStatus.RECEIVABLE, advance.status());
        assertEquals(ADVANCE, advance.advanceAmount());
        assertEquals(ADVANCE, advance.remaining());

        var events = advance.pullDomainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(AdvanceCreated.class, events.getFirst());
    }

    @Test
    void marksReceivedAndAppliesInParts() {
        var advance = createAdvance();
        advance.pullDomainEvents();

        advance.markReceived();
        assertEquals(AdvanceStatus.RECEIVED, advance.status());
        assertInstanceOf(AdvanceReceived.class, advance.pullDomainEvents().getFirst());

        Money part1 = Money.of(new BigDecimal("5000.00"), "IDR");
        advance.apply(part1);
        assertEquals(AdvanceStatus.PARTIALLY_APPLIED, advance.status());
        assertEquals(Money.of(new BigDecimal("15000.00"), "IDR"), advance.remaining());
        assertInstanceOf(AdvanceApplied.class, advance.pullDomainEvents().getFirst());

        Money part2 = Money.of(new BigDecimal("15000.00"), "IDR");
        advance.apply(part2);
        assertEquals(AdvanceStatus.FULLY_APPLIED, advance.status());
        assertEquals(Money.of(new BigDecimal("0.00"), "IDR"), advance.remaining());
    }

    @Test
    void cannotApplyBeforeReceiving() {
        var advance = createAdvance();
        assertThrows(InvalidStateException.class, () ->
                advance.apply(Money.of(new BigDecimal("1000.00"), "IDR"))
        );
    }

    @Test
    void refundsRemainingAdvance() {
        var advance = createAdvance();
        advance.markReceived();
        advance.apply(Money.of(new BigDecimal("5000.00"), "IDR"));
        advance.pullDomainEvents();

        advance.refund();
        assertEquals(AdvanceStatus.REFUNDED, advance.status());
        assertInstanceOf(AdvanceRefunded.class, advance.pullDomainEvents().getFirst());
    }
}
