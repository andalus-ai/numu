package tech.kayys.syirkah.workforce.domain.position.policy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.workforce.domain.employment.Employment;
import tech.kayys.syirkah.workforce.domain.employment.EmploymentId;
import tech.kayys.syirkah.workforce.domain.employment.EmploymentType;
import tech.kayys.syirkah.workforce.domain.employment.OrganizationRef;
import tech.kayys.syirkah.workforce.domain.employment.PositionRef;
import tech.kayys.syirkah.workforce.domain.position.Position;
import tech.kayys.syirkah.workforce.domain.position.PositionId;
import tech.kayys.syirkah.foundation.domain.ref.PersonRef;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.workforce.domain.worker.Worker;
import tech.kayys.syirkah.workforce.domain.worker.WorkerId;
import tech.kayys.syirkah.workforce.domain.worker.WorkerType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionAssignmentPolicyTest {

    private PositionAssignmentPolicy policy;
    private OrganizationRef orgRef;
    private Worker worker;
    private Employment employment;
    private Position position;

    @BeforeEach
    void setUp() {
        policy = new PositionAssignmentPolicy();
        orgRef = OrganizationRef.of(UUID.randomUUID());

        worker = Worker.create(
                WorkerId.generate(),
                TenantId.of(UUID.randomUUID()),
                PersonRef.of(UUID.randomUUID()),
                WorkerType.EMPLOYEE,
                "admin",
                Instant.now()
        );

        position = Position.create(
                PositionId.generate(),
                orgRef,
                "Software Engineer",
                "Backend developer",
                null
        );

        employment = Employment.start(
                EmploymentId.generate(),
                worker.id(),
                orgRef,
                PositionRef.of(position.id().value()),
                EmploymentType.PERMANENT,
                LocalDate.of(2026, 1, 1)
        );
    }

    @Test
    void shouldPassValidAssignment() {
        assertThatCode(() -> policy.validateAssignment(worker, employment, position, LocalDate.of(2026, 1, 1)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenPositionNotActive() {
        position.deactivate();
        assertThatThrownBy(() -> policy.validateAssignment(worker, employment, position, LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POSITION_NOT_ACTIVE");
    }

    @Test
    void shouldFailWhenWorkerSuspended() {
        worker.suspend("admin", Instant.now(), "Investigation");
        assertThatThrownBy(() -> policy.validateAssignment(worker, employment, position, LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WORKER_NOT_ACTIVE");
    }

    @Test
    void shouldFailWhenWorkerDoesNotMatchEmployment() {
        var anotherWorker = Worker.create(
                WorkerId.generate(),
                TenantId.of(UUID.randomUUID()),
                PersonRef.of(UUID.randomUUID()),
                WorkerType.EMPLOYEE,
                "admin",
                Instant.now()
        );

        assertThatThrownBy(() -> policy.validateAssignment(anotherWorker, employment, position, LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EMPLOYMENT_WORKER_MISMATCH");
    }

    @Test
    void shouldFailWhenOrganizationMismatch() {
        var differentOrgRef = OrganizationRef.of(UUID.randomUUID());
        var positionOtherOrg = Position.create(
                PositionId.generate(),
                differentOrgRef,
                "Architect",
                "System design",
                null
        );

        assertThatThrownBy(() -> policy.validateAssignment(worker, employment, positionOtherOrg, LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ORGANIZATION_MISMATCH");
    }

    @Test
    void shouldFailWhenStartDateBeforeEmployment() {
        assertThatThrownBy(() -> policy.validateAssignment(worker, employment, position, LocalDate.of(2025, 12, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DATE_BEFORE_EMPLOYMENT");
    }
}
