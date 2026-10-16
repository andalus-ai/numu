package tech.kayys.syirkah.workforce.domain.employment;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.workforce.domain.employment.event.EmploymentStarted;
import tech.kayys.syirkah.workforce.domain.employment.event.EmploymentSuspended;
import tech.kayys.syirkah.workforce.domain.employment.event.EmploymentTerminated;
import tech.kayys.syirkah.workforce.domain.worker.WorkerId;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmploymentTest {

    @Test
    void shouldStartEmploymentAndEmitEvent() {
        var id = EmploymentId.generate();
        var workerId = WorkerId.generate();
        var orgRef = OrganizationRef.of(UUID.randomUUID());
        var posRef = PositionRef.of(UUID.randomUUID());
        var startDate = LocalDate.of(2026, 1, 1);

        var employment = Employment.start(id, workerId, orgRef, posRef, EmploymentType.PERMANENT, startDate);

        assertThat(employment.id()).isEqualTo(id);
        assertThat(employment.workerId()).isEqualTo(workerId);
        assertThat(employment.organization()).isEqualTo(orgRef);
        assertThat(employment.position()).isEqualTo(posRef);
        assertThat(employment.status()).isEqualTo(EmploymentStatus.ACTIVE);

        var events = employment.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(EmploymentStarted.class);
    }

    @Test
    void shouldSuspendResumeAndTerminateEmployment() {
        var employment = Employment.start(
                EmploymentId.generate(),
                WorkerId.generate(),
                OrganizationRef.of(UUID.randomUUID()),
                PositionRef.of(UUID.randomUUID()),
                EmploymentType.FIXED_TERM,
                LocalDate.of(2026, 1, 1)
        );
        employment.pullDomainEvents();

        employment.suspend("Unpaid sabbatical");
        assertThat(employment.status()).isEqualTo(EmploymentStatus.SUSPENDED);
        assertThat(employment.pullDomainEvents().get(0)).isInstanceOf(EmploymentSuspended.class);

        employment.resume();
        assertThat(employment.status()).isEqualTo(EmploymentStatus.ACTIVE);

        employment.terminate(LocalDate.of(2026, 12, 31), "Contract completed");
        assertThat(employment.status()).isEqualTo(EmploymentStatus.TERMINATED);
        assertThat(employment.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(employment.pullDomainEvents().get(1)).isInstanceOf(EmploymentTerminated.class);
    }

    @Test
    void shouldRejectTerminationBeforeStartDate() {
        var employment = Employment.start(
                EmploymentId.generate(),
                WorkerId.generate(),
                OrganizationRef.of(UUID.randomUUID()),
                PositionRef.of(UUID.randomUUID()),
                EmploymentType.PERMANENT,
                LocalDate.of(2026, 6, 1)
        );

        assertThatThrownBy(() -> employment.terminate(LocalDate.of(2026, 5, 1), "Invalid date"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
