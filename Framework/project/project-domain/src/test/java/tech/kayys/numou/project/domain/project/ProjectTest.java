package tech.kayys.syirkah.project.domain.project;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.project.domain.event.ProjectCancelled;
import tech.kayys.syirkah.project.domain.event.ProjectCompleted;
import tech.kayys.syirkah.project.domain.event.ProjectCreated;
import tech.kayys.syirkah.project.domain.event.ProjectPlanned;
import tech.kayys.syirkah.project.domain.event.ProjectPutOnHold;
import tech.kayys.syirkah.project.domain.event.ProjectResumed;
import tech.kayys.syirkah.project.domain.event.ProjectStarted;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Project aggregate lifecycle")
class ProjectTest {

    private static final UUID CUSTOMER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final DateRange PLANNED_PERIOD =
            new DateRange(
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 12, 31)
            );

    private static Project newProject() {
        return Project.create(
                ProjectId.generate(),
                ProjectNumber.of("PRJ-101"),
                "Core Banking Cloud",
                ProjectType.CLIENT_BILLABLE,
                CUSTOMER_ID
        );
    }

    private static Project plannedProject() {
        var project = newProject();
        project.plan(PLANNED_PERIOD);
        project.pullDomainEvents();
        return project;
    }

    @Test
    @DisplayName("create() starts DRAFT and raises ProjectCreated")
    void createStartsDraftAndRaisesProjectCreated() {
        var id = ProjectId.generate();

        var project = Project.create(
                id,
                ProjectNumber.of("PRJ-101"),
                "Core Banking Cloud",
                ProjectType.CLIENT_BILLABLE,
                CUSTOMER_ID
        );

        assertEquals(id, project.id());
        assertEquals(ProjectStatus.DRAFT, project.status());
        assertEquals("PRJ-101", project.projectNumber().value());
        assertEquals("Core Banking Cloud", project.name());
        assertEquals(ProjectType.CLIENT_BILLABLE, project.type());
        assertEquals(CUSTOMER_ID, project.customerId());
        assertNull(project.plannedPeriod());

        var events = project.pullDomainEvents();
        assertEquals(1, events.size());

        var created = assertInstanceOf(ProjectCreated.class, events.getFirst());
        assertEquals(id, created.projectId());
        assertEquals("PRJ-101", created.projectNumber());
        assertEquals("project.project-created", created.eventType());

        // pullDomainEvents() clears the internal list
        assertTrue(project.pullDomainEvents().isEmpty());
    }

    @Test
    @DisplayName("plan() records the period and raises ProjectPlanned")
    void planRecordsPeriod() {
        var project = newProject();
        project.pullDomainEvents();

        project.plan(PLANNED_PERIOD);

        assertEquals(ProjectStatus.PLANNED, project.status());
        assertEquals(PLANNED_PERIOD, project.plannedPeriod());

        var planned = assertInstanceOf(
                ProjectPlanned.class,
                project.pullDomainEvents().getFirst()
        );
        assertEquals(PLANNED_PERIOD, planned.plannedPeriod());
        assertEquals("project.project-planned", planned.eventType());
    }

    @Test
    @DisplayName("full happy path raises one event per transition")
    void fullLifecycleRaisesOneEventPerTransition() {
        var project = newProject();

        project.plan(PLANNED_PERIOD);
        project.start();
        assertEquals(ProjectStatus.ACTIVE, project.status());

        project.putOnHold();
        assertEquals(ProjectStatus.ON_HOLD, project.status());

        project.resume();
        assertEquals(ProjectStatus.ACTIVE, project.status());

        project.complete();
        assertEquals(ProjectStatus.COMPLETED, project.status());

        var events = project.pullDomainEvents();

        assertEquals(
                List.of(
                        ProjectCreated.class,
                        ProjectPlanned.class,
                        ProjectStarted.class,
                        ProjectPutOnHold.class,
                        ProjectResumed.class,
                        ProjectCompleted.class
                ),
                events.stream().map(Object::getClass).toList()
        );
    }

    @Test
    @DisplayName("transitions from the wrong status are rejected")
    void transitionsFromWrongStatusAreRejected() {
        var project = newProject();

        assertThrows(
                InvalidProjectStateException.class,
                project::start,
                "a DRAFT project cannot be started"
        );
        assertThrows(
                InvalidProjectStateException.class,
                project::complete,
                "a DRAFT project cannot be completed"
        );
        assertThrows(
                InvalidProjectStateException.class,
                project::putOnHold,
                "a DRAFT project cannot be put on hold"
        );
        assertThrows(
                InvalidProjectStateException.class,
                project::resume,
                "only an ON_HOLD project can be resumed"
        );
    }

    @Test
    @DisplayName("plan() can only run once, from DRAFT")
    void planCanOnlyRunFromDraft() {
        var project = newProject();
        project.plan(PLANNED_PERIOD);

        assertThrows(
                InvalidProjectStateException.class,
                () -> project.plan(PLANNED_PERIOD)
        );
    }

    @Test
    @DisplayName("cancel() is allowed until completion")
    void cancelIsAllowedUntilCompletion() {
        var draft = newProject();
        draft.cancel();
        assertEquals(ProjectStatus.CANCELLED, draft.status());
        assertInstanceOf(
                ProjectCancelled.class,
                draft.pullDomainEvents().getLast()
        );

        assertThrows(
                InvalidProjectStateException.class,
                draft::cancel,
                "an already cancelled project cannot be cancelled again"
        );

        var completed = newProject();
        completed.plan(PLANNED_PERIOD);
        completed.start();
        completed.complete();

        assertThrows(
                InvalidProjectStateException.class,
                completed::cancel,
                "a completed project cannot be cancelled"
        );
    }

    @Test
    @DisplayName("an active project can be cancelled")
    void activeProjectCanBeCancelled() {
        var project = plannedProject();
        project.start();

        project.cancel();

        assertEquals(ProjectStatus.CANCELLED, project.status());
        assertInstanceOf(
                ProjectCancelled.class,
                project.pullDomainEvents().getLast()
        );
    }

    @Test
    @DisplayName("name is required, trimmed and length-limited")
    void nameIsValidated() {
        assertThrows(
                NullPointerException.class,
                () -> Project.create(
                        ProjectId.generate(),
                        ProjectNumber.of("PRJ-1"),
                        null,
                        ProjectType.INTERNAL,
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> Project.create(
                        ProjectId.generate(),
                        ProjectNumber.of("PRJ-1"),
                        "   ",
                        ProjectType.INTERNAL,
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> Project.create(
                        ProjectId.generate(),
                        ProjectNumber.of("PRJ-1"),
                        "x".repeat(256),
                        ProjectType.INTERNAL,
                        null
                )
        );

        var project = Project.create(
                ProjectId.generate(),
                ProjectNumber.of("PRJ-1"),
                "  Warehouse Expansion  ",
                ProjectType.INTERNAL,
                null
        );

        assertEquals("Warehouse Expansion", project.name());
        assertNull(project.customerId(), "internal projects need no customer");
    }

    @Test
    @DisplayName("type and project number are mandatory")
    void typeAndProjectNumberAreMandatory() {
        assertThrows(
                NullPointerException.class,
                () -> Project.create(
                        ProjectId.generate(),
                        ProjectNumber.of("PRJ-1"),
                        "Warehouse Expansion",
                        null,
                        null
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> Project.create(
                        ProjectId.generate(),
                        null,
                        "Warehouse Expansion",
                        ProjectType.INTERNAL,
                        null
                )
        );
    }

    @Test
    @DisplayName("plan() rejects a null period")
    void planRejectsNullPeriod() {
        var project = newProject();

        assertThrows(
                NullPointerException.class,
                () -> project.plan(null)
        );
    }

    @Test
    @DisplayName("ProjectId equals by value and rejects null")
    void projectIdEquality() {
        var uuid = UUID.randomUUID();

        assertEquals(ProjectId.of(uuid), ProjectId.of(uuid));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProjectId.of(null)
        );
    }
}
