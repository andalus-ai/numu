package tech.kayys.syirkah.project.domain.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("RiskTreatmentAction aggregate")
class RiskTreatmentActionTest {

    private static final ProjectId PROJECT = ProjectId.generate();
    private static final RiskId RISK = RiskId.generate();
    private static final LocalDate DUE = LocalDate.of(2026, 3, 31);

    private static RiskTreatmentAction action() {
        return RiskTreatmentAction.create(
                RiskTreatmentActionId.generate(),
                PROJECT,
                RISK,
                "Qualify second supplier",
                "Run RFQ with two alternates",
                UUID.randomUUID(),
                DUE,
                Money.of(5_000_000L, "IDR")
        );
    }

    @Test
    void startsOpenAndRejectsNegativeCost() {
        var action = action();

        assertEquals(RiskActionStatus.OPEN, action.status());
        assertEquals(RISK, action.riskId());
        assertEquals(DUE, action.dueDate());

        assertThrows(IllegalArgumentException.class, () -> RiskTreatmentAction.create(
                RiskTreatmentActionId.generate(),
                PROJECT,
                RISK,
                "Title",
                "Description",
                UUID.randomUUID(),
                DUE,
                Money.of(-1L, "IDR")
        ));
    }

    @Test
    void happyPathOpenToCompleted() {
        var action = action();

        action.start();
        assertEquals(RiskActionStatus.IN_PROGRESS, action.status());

        action.complete();
        assertEquals(RiskActionStatus.COMPLETED, action.status());
    }

    @Test
    void illegalTransitionsAreRejected() {
        var action = action();

        assertThrows(InvalidRiskStateException.class, action::complete);
        action.start();
        assertThrows(InvalidRiskStateException.class, action::start);

        action.complete();
        assertThrows(InvalidRiskStateException.class, action::cancel);
    }
}
