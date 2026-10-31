package tech.kayys.numou.workforce.application.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.workforce.application.support.RecordingEventPublisher;
import tech.kayys.numou.workforce.domain.employment.Employment;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.employment.EmploymentStatus;
import tech.kayys.numou.workforce.domain.employment.EmploymentType;
import tech.kayys.numou.workforce.domain.employment.OrganizationRef;
import tech.kayys.numou.workforce.domain.employment.event.EmploymentStarted;
import tech.kayys.numou.workforce.domain.position.Position;
import tech.kayys.numou.workforce.domain.position.PositionAssignment;
import tech.kayys.numou.workforce.domain.position.PositionAssignmentId;
import tech.kayys.numou.workforce.domain.position.PositionId;
import tech.kayys.numou.foundation.domain.ref.PersonRef;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.worker.Worker;
import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.domain.worker.WorkerStatus;
import tech.kayys.numou.workforce.domain.worker.WorkerType;
import tech.kayys.numou.workforce.domain.worker.event.WorkerCreated;
import tech.kayys.numou.workforce.spi.port.EmploymentRepository;
import tech.kayys.numou.workforce.spi.port.PositionAssignmentRepository;
import tech.kayys.numou.workforce.spi.port.PositionRepository;
import tech.kayys.numou.workforce.spi.port.WorkerRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class WorkforceCommandHandlerTest {

    private RecordingEventPublisher eventPublisher;

    // Test fake repositories
    private FakeWorkerRepository workerRepository;
    private FakeEmploymentRepository employmentRepository;
    private FakePositionRepository positionRepository;
    private FakePositionAssignmentRepository assignmentRepository;

    // Handlers under test
    private RegisterWorkerHandler registerWorkerHandler;
    private SuspendWorkerHandler suspendWorkerHandler;
    private StartEmploymentHandler startEmploymentHandler;
    private AssignPositionHandler assignPositionHandler;

    @BeforeEach
    void setUp() {
        eventPublisher = new RecordingEventPublisher();
        workerRepository = new FakeWorkerRepository();
        employmentRepository = new FakeEmploymentRepository();
        positionRepository = new FakePositionRepository();
        assignmentRepository = new FakePositionAssignmentRepository();

        registerWorkerHandler = new RegisterWorkerHandler(workerRepository, eventPublisher);
        suspendWorkerHandler = new SuspendWorkerHandler(workerRepository, eventPublisher);
        startEmploymentHandler = new StartEmploymentHandler(employmentRepository, workerRepository, eventPublisher);
        assignPositionHandler = new AssignPositionHandler(positionRepository, workerRepository, employmentRepository, assignmentRepository, eventPublisher);
    }

    @Test
    void shouldRegisterWorkerAndPublishEvent() {
        var personId = UUID.randomUUID();
        var command = new RegisterWorkerCommand(TenantId.generate(), personId, WorkerType.EMPLOYEE, "admin-1");

        var result = registerWorkerHandler.handle(command).await().indefinitely();
        assertThat(result.isSuccess()).isTrue();
        var workerId = result.orElseThrow();

        var saved = workerRepository.findById(workerId).toCompletableFuture().join().orElseThrow();
        assertThat(saved.status()).isEqualTo(WorkerStatus.ACTIVE);
        assertThat(saved.type()).isEqualTo(WorkerType.EMPLOYEE);
        assertThat(eventPublisher.publishedTypes()).contains(WorkerCreated.class);
    }

    @Test
    void shouldRejectDuplicateWorkerForSamePersonAndTenant() {
        var tenantId = TenantId.generate();
        var personId = UUID.randomUUID();

        var first = registerWorkerHandler.handle(
                new RegisterWorkerCommand(tenantId, personId, WorkerType.EMPLOYEE, "admin")
        ).await().indefinitely();
        var second = registerWorkerHandler.handle(
                new RegisterWorkerCommand(tenantId, personId, WorkerType.CONTRACTOR, "admin")
        ).await().indefinitely();

        assertThat(first.isSuccess()).isTrue();
        assertThat(second.isSuccess()).isFalse();
    }

    @Test
    void shouldAllowSamePersonToHaveWorkerRecordInDifferentTenants() {
        var personId = UUID.randomUUID();

        var first = registerWorkerHandler.handle(
                new RegisterWorkerCommand(TenantId.generate(), personId, WorkerType.EMPLOYEE, "admin")
        ).await().indefinitely();
        var second = registerWorkerHandler.handle(
                new RegisterWorkerCommand(TenantId.generate(), personId, WorkerType.CONTRACTOR, "admin")
        ).await().indefinitely();

        assertThat(first.isSuccess()).isTrue();
        assertThat(second.isSuccess()).isTrue();
    }

    @Test
    void shouldStartEmploymentForActiveWorker() {
        var personId = UUID.randomUUID();
        var workerId = registerWorkerHandler.handle(new RegisterWorkerCommand(TenantId.generate(), personId, WorkerType.EMPLOYEE, "admin"))
                .await().indefinitely().orElseThrow();

        var orgId = UUID.randomUUID();
        var command = new StartEmploymentCommand(workerId, orgId, null, EmploymentType.PERMANENT, LocalDate.of(2026, 1, 1));

        var result = startEmploymentHandler.handle(command).await().indefinitely();
        assertThat(result.isSuccess()).isTrue();
        var employmentId = result.orElseThrow();

        var saved = employmentRepository.findById(employmentId).toCompletableFuture().join().orElseThrow();
        assertThat(saved.status()).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(eventPublisher.publishedTypes()).contains(EmploymentStarted.class);
    }

    @Test
    void shouldAssignPositionWithPolicyValidation() {
        var personId = UUID.randomUUID();
        var workerId = registerWorkerHandler.handle(new RegisterWorkerCommand(TenantId.generate(), personId, WorkerType.EMPLOYEE, "admin"))
                .await().indefinitely().orElseThrow();

        var orgId = UUID.randomUUID();
        var orgRef = OrganizationRef.of(orgId);

        var position = Position.create(PositionId.generate(), orgRef, "Lead Engineer", "Tech lead", null);
        positionRepository.save(position).toCompletableFuture().join();

        var employmentId = startEmploymentHandler.handle(
                new StartEmploymentCommand(workerId, orgId, position.id().value(), EmploymentType.PERMANENT, LocalDate.of(2026, 1, 1))
        ).await().indefinitely().orElseThrow();

        var assignCommand = new AssignPositionCommand(position.id(), workerId, employmentId, LocalDate.of(2026, 1, 1));
        var result = assignPositionHandler.handle(assignCommand).await().indefinitely();

        assertThat(result.isSuccess()).isTrue();
        var assignmentId = result.orElseThrow();
        assertThat(assignmentRepository.existsById(assignmentId).toCompletableFuture().join()).isTrue();
    }

    // --- Fake Repository Implementations ---

    private static class FakeWorkerRepository implements WorkerRepository {
        private final Map<WorkerId, Worker> map = new ConcurrentHashMap<>();
        @Override public CompletionStage<Worker> save(Worker w) { map.put(w.id(), w); return CompletableFuture.completedFuture(w); }
        @Override public CompletionStage<Optional<Worker>> findById(WorkerId id) { return CompletableFuture.completedFuture(Optional.ofNullable(map.get(id))); }
        @Override public CompletionStage<Optional<Worker>> findById(TenantId tenantId, WorkerId id) {
            return CompletableFuture.completedFuture(Optional.ofNullable(map.get(id)).filter(worker -> worker.tenantId().equals(tenantId)));
        }
        @Override public CompletionStage<Optional<Worker>> findByPersonRef(TenantId tenantId, PersonRef p) {
            return CompletableFuture.completedFuture(map.values().stream()
                    .filter(w -> w.tenantId().equals(tenantId) && w.person().equals(p)).findFirst());
        }
        @Override public CompletionStage<Boolean> existsById(WorkerId id) { return CompletableFuture.completedFuture(map.containsKey(id)); }
        @Override public CompletionStage<Boolean> existsById(TenantId tenantId, WorkerId id) {
            return CompletableFuture.completedFuture(Optional.ofNullable(map.get(id))
                    .filter(worker -> worker.tenantId().equals(tenantId)).isPresent());
        }
        @Override public CompletionStage<Void> delete(Worker w) { map.remove(w.id()); return CompletableFuture.completedFuture(null); }
        @Override public CompletionStage<Void> deleteById(WorkerId id) { map.remove(id); return CompletableFuture.completedFuture(null); }
        @Override public CompletionStage<Void> deleteById(TenantId tenantId, WorkerId id) {
            map.computeIfPresent(id, (key, worker) -> worker.tenantId().equals(tenantId) ? null : worker);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class FakeEmploymentRepository implements EmploymentRepository {
        private final Map<EmploymentId, Employment> map = new ConcurrentHashMap<>();
        @Override public CompletionStage<Employment> save(Employment e) { map.put(e.id(), e); return CompletableFuture.completedFuture(e); }
        @Override public CompletionStage<Optional<Employment>> findById(EmploymentId id) { return CompletableFuture.completedFuture(Optional.ofNullable(map.get(id))); }
        @Override public CompletionStage<List<Employment>> findByWorkerId(WorkerId w) {
            return CompletableFuture.completedFuture(map.values().stream().filter(e -> e.workerId().equals(w)).toList());
        }
        @Override public CompletionStage<List<Employment>> findByOrganizationRef(OrganizationRef o) {
            return CompletableFuture.completedFuture(map.values().stream().filter(e -> e.organization().equals(o)).toList());
        }
        @Override public CompletionStage<Boolean> existsById(EmploymentId id) { return CompletableFuture.completedFuture(map.containsKey(id)); }
        @Override public CompletionStage<Void> delete(Employment e) { map.remove(e.id()); return CompletableFuture.completedFuture(null); }
        @Override public CompletionStage<Void> deleteById(EmploymentId id) { map.remove(id); return CompletableFuture.completedFuture(null); }
    }

    private static class FakePositionRepository implements PositionRepository {
        private final Map<PositionId, Position> map = new ConcurrentHashMap<>();
        @Override public CompletionStage<Position> save(Position p) { map.put(p.id(), p); return CompletableFuture.completedFuture(p); }
        @Override public CompletionStage<Optional<Position>> findById(PositionId id) { return CompletableFuture.completedFuture(Optional.ofNullable(map.get(id))); }
        @Override public CompletionStage<List<Position>> findByOrganizationRef(OrganizationRef o) {
            return CompletableFuture.completedFuture(map.values().stream().filter(p -> p.organization().equals(o)).toList());
        }
        @Override public CompletionStage<Boolean> existsById(PositionId id) { return CompletableFuture.completedFuture(map.containsKey(id)); }
        @Override public CompletionStage<Void> delete(Position p) { map.remove(p.id()); return CompletableFuture.completedFuture(null); }
        @Override public CompletionStage<Void> deleteById(PositionId id) { map.remove(id); return CompletableFuture.completedFuture(null); }
    }

    private static class FakePositionAssignmentRepository implements PositionAssignmentRepository {
        private final Map<PositionAssignmentId, PositionAssignment> map = new ConcurrentHashMap<>();
        @Override public CompletionStage<PositionAssignment> save(PositionAssignment a) { map.put(a.id(), a); return CompletableFuture.completedFuture(a); }
        @Override public CompletionStage<Optional<PositionAssignment>> findById(PositionAssignmentId id) { return CompletableFuture.completedFuture(Optional.ofNullable(map.get(id))); }
        @Override public CompletionStage<List<PositionAssignment>> findByWorkerId(WorkerId w) {
            return CompletableFuture.completedFuture(map.values().stream().filter(a -> a.workerId().equals(w)).toList());
        }
        @Override public CompletionStage<List<PositionAssignment>> findByPositionId(PositionId p) {
            return CompletableFuture.completedFuture(map.values().stream().filter(a -> a.positionId().equals(p)).toList());
        }
        @Override public CompletionStage<Boolean> existsById(PositionAssignmentId id) { return CompletableFuture.completedFuture(map.containsKey(id)); }
        @Override public CompletionStage<Void> delete(PositionAssignment a) { map.remove(a.id()); return CompletableFuture.completedFuture(null); }
        @Override public CompletionStage<Void> deleteById(PositionAssignmentId id) { map.remove(id); return CompletableFuture.completedFuture(null); }
    }
}
