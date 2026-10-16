package tech.kayys.syirkah.project.domain.commercial;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.event.RetentionForfeited;
import tech.kayys.syirkah.project.domain.commercial.event.RetentionHeld;
import tech.kayys.syirkah.project.domain.commercial.event.RetentionReleased;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProjectRetention domain invariants and lifecycle")
class ProjectRetentionTest {

    private static final ProjectId PROJECT_ID = ProjectId.generate();
    private static final ProjectContractId CONTRACT_ID = ProjectContractId.generate();
    private static final Money HELD = Money.of(new BigDecimal("10000.00"), "IDR");

    private ProjectRetention createRetention() {
        return ProjectRetention.create(
                ProjectRetentionId.generate(),
                PROJECT_ID,
                CONTRACT_ID,
                HELD
        );
    }

    @Test
    void createsRetentionAndRaisesHeldEvent() {
        var retention = createRetention();

        assertEquals(RetentionStatus.HELD, retention.status());
        assertEquals(HELD, retention.heldAmount());
        assertEquals(HELD, retention.remaining());

        var events = retention.pullDomainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(RetentionHeld.class, events.getFirst());
    }

    @Test
    void releasesInPartsUntilFullyReleased() {
        var retention = createRetention();
        retention.pullDomainEvents();

        Money half = Money.of(new BigDecimal("5000.00"), "IDR");
        retention.release(half);
        assertEquals(RetentionStatus.PARTIALLY_RELEASED, retention.status());
        assertEquals(half, retention.remaining());
        assertInstanceOf(RetentionReleased.class, retention.pullDomainEvents().getFirst());

        retention.release(half);
        assertEquals(RetentionStatus.FULLY_RELEASED, retention.status());
        assertEquals(Money.of(new BigDecimal("0.00"), "IDR"), retention.remaining());
    }

    @Test
    void cannotReleaseMoreThanRemaining() {
        var retention = createRetention();
        assertThrows(IllegalArgumentException.class, () ->
                retention.release(Money.of(new BigDecimal("15000.00"), "IDR"))
        );
    }

    @Test
    void forfeitsRemainingRetention() {
        var retention = createRetention();
        retention.release(Money.of(new BigDecimal("3000.00"), "IDR"));
        retention.pullDomainEvents();

        retention.forfeit();
        assertEquals(RetentionStatus.FORFEITED, retention.status());
        assertInstanceOf(RetentionForfeited.class, retention.pullDomainEvents().getFirst());
    }

    @Test
    void cannotReleaseForfeitedRetention() {
        var retention = createRetention();
        retention.forfeit();

        assertThrows(InvalidStateException.class, () ->
                retention.release(Money.of(new BigDecimal("1000.00"), "IDR"))
        );
    }
}
