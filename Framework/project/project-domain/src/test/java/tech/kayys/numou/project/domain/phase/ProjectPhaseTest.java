package tech.kayys.syirkah.project.domain.phase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.project.domain.event.PhaseCompleted;
import tech.kayys.syirkah.project.domain.event.PhaseCreated;
import tech.kayys.syirkah.project.domain.event.PhaseStarted;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("ProjectPhase aggregate lifecycle")
class ProjectPhaseTest {

    private static final ProjectId PROJECT_ID = ProjectId.generate();

    private static final DateRange PERIOD =
            new DateRange(
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 3, 31)
            );

    private static ProjectPhase newPhase() {
        return ProjectPhase.create(
                ProjectPhaseId.generate(),
                PROJECT_ID,
                1,
                "Foundation Construction",
                PhaseType.EXECUTION
        );
    }

    @Test
    @DisplayName("create() starts DRAFT and raises PhaseCreated")
    void createStartsDraft() {
        var id = ProjectPhaseId.generate();

        var phase = ProjectPhase.create(
                id,
                PROJECT_ID,
                2,
                "Structural Construction",
                PhaseType.EXECUTION
        );

        assertEquals(id, phase.id());
        assertEquals(PROJECT_ID, phase.projectId());
        assertEquals(2, phase.sequence());
        assertEquals("Structural Construction", phase.name());
        assertEquals(PhaseType.EXECUTION, phase.type());
        assertEquals(PhaseStatus.DRAFT, phase.status());
        assertNull(phase.plannedPeriod());

        var created = assertInstanceOf(
                PhaseCreated.class,
                phase.pullDomainEvents().getFirst()
        );
        assertEquals(id, created.phaseId());
        assertEquals(PROJECT_ID, created.projectId());
        assertEquals("Structural Construction", created.name());
        assertEquals("project.phase-created", created.eventType());
    }

    @Test
    @DisplayName("plan() and start() drive the phase to ACTIVE")
    void planThenStart() {
        var phase = newPhase();
        phase.pullDomainEvents();

        phase.plan(PERIOD);
        assertEquals(PhaseStatus.PLANNED, phase.status());
        assertEquals(PERIOD, phase.plannedPeriod());

        phase.start();

        assertEquals(PhaseStatus.ACTIVE, phase.status());
        assertInstanceOf(
                PhaseStarted.class,
                phase.pullDomainEvents().getFirst()
        );
    }

    @Test
    @DisplayName("hold / resume / complete transitions")
    void holdResumeComplete() {
        var phase = newPhase();
        phase.plan(PERIOD);
        phase.start();

        phase.putOnHold();
        assertEquals(PhaseStatus.ON_HOLD, phase.status());

        phase.resume();
        assertEquals(PhaseStatus.ACTIVE, phase.status());

        phase.complete();
        assertEquals(PhaseStatus.COMPLETED, phase.status());
        assertInstanceOf(
                PhaseCompleted.class,
                phase.pullDomainEvents().getLast()
        );
    }

    @Test
    @DisplayName("invalid transitions are rejected")
    void invalidTransitionsAreRejected() {
        var phase = newPhase();

        assertThrows(
                InvalidPhaseStateException.class,
                phase::start,
                "a DRAFT phase cannot be started"
        );
        assertThrows(
                InvalidPhaseStateException.class,
                phase::complete,
                "a DRAFT phase cannot be completed"
        );
        assertThrows(
                InvalidPhaseStateException.class,
                phase::putOnHold
        );
        assertThrows(
                InvalidPhaseStateException.class,
                phase::resume
        );
        assertThrows(
                NullPointerException.class,
                () -> phase.plan(null)
        );
    }

    @Test
    @DisplayName("cancel() is allowed until completion")
    void cancelIsAllowedUntilCompletion() {
        var cancelled = newPhase();
        cancelled.cancel();
        assertEquals(PhaseStatus.CANCELLED, cancelled.status());

        assertThrows(
                InvalidPhaseStateException.class,
                cancelled::cancel
        );

        var completed = newPhase();
        completed.plan(PERIOD);
        completed.start();
        completed.complete();

        assertThrows(
                InvalidPhaseStateException.class,
                completed::cancel
        );
    }

    @Test
    @DisplayName("sequence, name and identifying are validated")
    void sequenceAndNameAreValidated() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProjectPhase.create(
                        ProjectPhaseId.generate(),
                        PROJECT_ID,
                        0,
                        "Preparation",
                        PhaseType.PREPARATION
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> ProjectPhase.create(
                        ProjectPhaseId.generate(),
                        PROJECT_ID,
                        1,
                        "  ",
                        PhaseType.PREPARATION
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> ProjectPhase.create(
                        ProjectPhaseId.generate(),
                        PROJECT_ID,
                        1,
                        "x".repeat(256),
                        PhaseType.PREPARATION
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> ProjectPhase.create(
                        ProjectPhaseId.generate(),
                        null,
                        1,
                        "Preparation",
                        PhaseType.PREPARATION
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> ProjectPhase.create(
                        ProjectPhaseId.generate(),
                        PROJECT_ID,
                        1,
                        "Preparation",
                        null
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> ProjectPhaseId.of(null)
        );
    }
}
