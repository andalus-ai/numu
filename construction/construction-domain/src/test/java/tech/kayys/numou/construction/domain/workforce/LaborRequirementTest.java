package tech.kayys.numou.construction.domain.workforce;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LaborRequirementTest {
    private static final LocalDate REQUIRED_DATE = LocalDate.parse("2026-11-02");

    @Test
    void rejectsNonPositiveHeadcount() {
        assertThrows(IllegalArgumentException.class,
                () -> LaborRequirement.create(UUID.randomUUID(), UUID.randomUUID(), "Electrician", 0, REQUIRED_DATE));
        assertThrows(IllegalArgumentException.class,
                () -> LaborRequirement.create(UUID.randomUUID(), UUID.randomUUID(), "Electrician", -1, REQUIRED_DATE));
    }

    @Test
    void rejectsBlankTrade() {
        assertThrows(IllegalArgumentException.class,
                () -> LaborRequirement.create(UUID.randomUUID(), UUID.randomUUID(), "  ", 1, REQUIRED_DATE));
    }

    @Test
    void requiresMatchingTradeAndDistinctWorkers() {
        var requirement = requirement(2);
        UUID workerId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> requirement.assignWorker(new LaborAssignment(workerId, "Plumber", REQUIRED_DATE)));

        requirement.assignWorker(new LaborAssignment(workerId, " electrician ", REQUIRED_DATE));
        assertThrows(IllegalArgumentException.class,
                () -> requirement.assignWorker(new LaborAssignment(workerId, "Electrician", REQUIRED_DATE)));
        assertEquals(1, requirement.assignments().size());
        assertEquals(LaborRequirementStatus.REQUESTED, requirement.status());
    }

    @Test
    void marksAssignedAtExactHeadcountAndRejectsExcessAssignments() {
        var requirement = requirement(1);
        requirement.assignWorker(new LaborAssignment(UUID.randomUUID(), "Electrician", REQUIRED_DATE));

        assertEquals(LaborRequirementStatus.ASSIGNED, requirement.status());
        assertEquals(1, requirement.assignments().size());
        assertThrows(IllegalStateException.class,
                () -> requirement.assignWorker(new LaborAssignment(UUID.randomUUID(), "Electrician", REQUIRED_DATE)));
        assertEquals(1, requirement.assignments().size());
    }

    private LaborRequirement requirement(int headcount) {
        return LaborRequirement.create(UUID.randomUUID(), UUID.randomUUID(), "Electrician", headcount, REQUIRED_DATE);
    }
}
