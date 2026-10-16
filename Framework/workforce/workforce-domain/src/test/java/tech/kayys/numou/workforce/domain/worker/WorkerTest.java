package tech.kayys.syirkah.workforce.domain.worker;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.ref.PersonRef;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.workforce.domain.worker.event.WorkerActivated;
import tech.kayys.syirkah.workforce.domain.worker.event.WorkerCreated;
import tech.kayys.syirkah.workforce.domain.worker.event.WorkerDeactivated;
import tech.kayys.syirkah.workforce.domain.worker.event.WorkerSuspended;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerTest {

    @Test
    void shouldCreateWorkerAndEmitWorkerCreatedEvent() {
        var id = WorkerId.generate();
        var tenantId = TenantId.of(UUID.randomUUID());
        var personRef = PersonRef.of(UUID.randomUUID());
        var now = Instant.now();

        var worker = Worker.create(id, tenantId, personRef, WorkerType.EMPLOYEE, "admin", now);

        assertThat(worker.id()).isEqualTo(id);
        assertThat(worker.status()).isEqualTo(WorkerStatus.ACTIVE);
        assertThat(worker.type()).isEqualTo(WorkerType.EMPLOYEE);
        assertThat(worker.person()).isEqualTo(personRef);

        var events = worker.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(WorkerCreated.class);
    }

    @Test
    void shouldSuspendAndReactivateWorker() {
        var worker = Worker.create(WorkerId.generate(), TenantId.of(UUID.randomUUID()),
                PersonRef.of(UUID.randomUUID()), WorkerType.CONTRACTOR, "admin", Instant.now());
        worker.pullDomainEvents();

        worker.suspend("admin", Instant.now(), "Investigation pending");
        assertThat(worker.status()).isEqualTo(WorkerStatus.SUSPENDED);
        var suspendEvents = worker.pullDomainEvents();
        assertThat(suspendEvents).hasSize(1);
        assertThat(suspendEvents.get(0)).isInstanceOf(WorkerSuspended.class);

        worker.activate("admin", Instant.now());
        assertThat(worker.status()).isEqualTo(WorkerStatus.ACTIVE);
        var activateEvents = worker.pullDomainEvents();
        assertThat(activateEvents).hasSize(1);
        assertThat(activateEvents.get(0)).isInstanceOf(WorkerActivated.class);
    }

    @Test
    void shouldDeactivateWorkerAndRejectActivation() {
        var worker = Worker.create(WorkerId.generate(), TenantId.of(UUID.randomUUID()),
                PersonRef.of(UUID.randomUUID()), WorkerType.EMPLOYEE, "admin", Instant.now());
        worker.pullDomainEvents();

        worker.deactivate("admin", Instant.now(), "Resigned");
        assertThat(worker.status()).isEqualTo(WorkerStatus.INACTIVE);
        assertThat(worker.pullDomainEvents()).hasSize(1);

        assertThatThrownBy(() -> worker.activate("admin", Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }
}
