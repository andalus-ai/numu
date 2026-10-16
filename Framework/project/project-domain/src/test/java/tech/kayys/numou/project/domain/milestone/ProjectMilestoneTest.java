package tech.kayys.syirkah.project.domain.milestone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.project.domain.event.MilestoneCancelled;
import tech.kayys.syirkah.project.domain.event.MilestoneCreated;
import tech.kayys.syirkah.project.domain.event.MilestoneReached;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("ProjectMilestone aggregate lifecycle")
class ProjectMilestoneTest {

    private static final ProjectId PROJECT_ID = ProjectId.generate();

    private static final LocalDate PLANNED_DATE = LocalDate.of(2026, 6, 30);

    private static ProjectMilestone newMilestone() {
        return ProjectMilestone.create(
                ProjectMilestoneId.generate(),
                PROJECT_ID,
                1,
                "Foundation Complete",
                MilestoneType.DELIVERABLE,
                PLANNED_DATE
        );
    }

    @Test
    @DisplayName("create() starts PLANNED and raises MilestoneCreated")
    void createStartsPlanned() {
        var id = ProjectMilestoneId.generate();

        var milestone = ProjectMilestone.create(
                id,
                PROJECT_ID,
                2,
                "Electrical Energized",
                MilestoneType.COMMISSIONING,
                PLANNED_DATE
        );

        assertEquals(id, milestone.id());
        assertEquals(PROJECT_ID, milestone.projectId());
        assertEquals(2, milestone.sequence());
        assertEquals("Electrical Energized", milestone.name());
        assertEquals(MilestoneType.COMMISSIONING, milestone.type());
        assertEquals(MilestoneStatus.PLANNED, milestone.status());
        assertEquals(PLANNED_DATE, milestone.plannedDate());
        assertNull(milestone.actualDate());

        var created = assertInstanceOf(
                MilestoneCreated.class,
                milestone.pullDomainEvents().getFirst()
        );
        assertEquals(id, created.milestoneId());
        assertEquals(PROJECT_ID, created.projectId());
        assertEquals("Electrical Energized", created.name());
        assertEquals("project.milestone-created", created.eventType());
    }

    @Test
    @DisplayName("reach() records the actual date and raises MilestoneReached")
    void reachRecordsActualDate() {
        var milestone = newMilestone();
        milestone.pullDomainEvents();

        var actual = LocalDate.of(2026, 7, 3);
        milestone.reach(actual);

        assertEquals(MilestoneStatus.REACHED, milestone.status());
        assertEquals(actual, milestone.actualDate());

        var reached = assertInstanceOf(
                MilestoneReached.class,
                milestone.pullDomainEvents().getFirst()
        );
        assertEquals(milestone.id(), reached.milestoneId());
        assertEquals(PROJECT_ID, reached.projectId());
        assertEquals(actual, reached.actualDate());
        assertEquals("project.milestone-reached", reached.eventType());
    }

    @Test
    @DisplayName("a milestone can be missed, but only while planned")
    void markMissedOnlyWhilePlanned() {
        var milestone = newMilestone();

        milestone.markMissed();
        assertEquals(MilestoneStatus.MISSED, milestone.status());

        assertThrows(
                InvalidMilestoneStateException.class,
                () -> milestone.reach(LocalDate.of(2026, 7, 3))
        );

        var reached = newMilestone();
        reached.reach(LocalDate.of(2026, 6, 29));

        assertThrows(
                InvalidMilestoneStateException.class,
                reached::markMissed
        );
    }

    @Test
    @DisplayName("cancel() raises MilestoneCancelled and is rejected once reached")
    void cancelRaisesEvent() {
        var milestone = newMilestone();

        milestone.cancel();

        assertEquals(MilestoneStatus.CANCELLED, milestone.status());
        assertInstanceOf(
                MilestoneCancelled.class,
                milestone.pullDomainEvents().getLast()
        );

        assertThrows(
                InvalidMilestoneStateException.class,
                milestone::cancel
        );

        var reached = newMilestone();
        reached.reach(LocalDate.of(2026, 6, 29));

        assertThrows(
                InvalidMilestoneStateException.class,
                reached::cancel
        );
    }

    @Test
    @DisplayName("sequence, name, type, planned date and identity are validated")
    void inputsAreValidated() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProjectMilestone.create(
                        ProjectMilestoneId.generate(),
                        PROJECT_ID,
                        0,
                        "Land Preparation Complete",
                        MilestoneType.DELIVERABLE,
                        PLANNED_DATE
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> ProjectMilestone.create(
                        ProjectMilestoneId.generate(),
                        PROJECT_ID,
                        1,
                        "  ",
                        MilestoneType.DELIVERABLE,
                        PLANNED_DATE
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> ProjectMilestone.create(
                        ProjectMilestoneId.generate(),
                        PROJECT_ID,
                        1,
                        "x".repeat(256),
                        MilestoneType.DELIVERABLE,
                        PLANNED_DATE
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> ProjectMilestone.create(
                        ProjectMilestoneId.generate(),
                        null,
                        1,
                        "Land Preparation Complete",
                        MilestoneType.DELIVERABLE,
                        PLANNED_DATE
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> ProjectMilestone.create(
                        ProjectMilestoneId.generate(),
                        PROJECT_ID,
                        1,
                        "Land Preparation Complete",
                        null,
                        PLANNED_DATE
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> ProjectMilestone.create(
                        ProjectMilestoneId.generate(),
                        PROJECT_ID,
                        1,
                        "Land Preparation Complete",
                        MilestoneType.DELIVERABLE,
                        null
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> ProjectMilestoneId.of(null)
        );
    }
}
