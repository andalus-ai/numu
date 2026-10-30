package tech.kayys.numou.workforce.adapter.memory;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.ref.PersonRef;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.worker.Worker;
import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.domain.worker.WorkerType;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryWorkerRepositoryTest {

    @Test
    void rejectsDuplicatePersonWithinTenantWithoutCorruptingIndex() {
        var repository = new InMemoryWorkerRepository();
        var tenant = tenant();
        var person = person();
        var original = worker(tenant, person);
        repository.save(original).toCompletableFuture().join();

        var duplicate = worker(tenant, person);

        assertThatThrownBy(() -> repository.save(duplicate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant and person");

        assertThat(repository.findByPersonRef(tenant, person).toCompletableFuture().join())
                .contains(original);
        assertThat(repository.findById(duplicate.id()).toCompletableFuture().join()).isEmpty();
    }

    @Test
    void tenantScopedIdLookupDoesNotRevealWorkerFromAnotherTenant() {
        var repository = new InMemoryWorkerRepository();
        var ownerTenant = tenant();
        var otherTenant = tenant();
        var worker = worker(ownerTenant, person());
        repository.save(worker).toCompletableFuture().join();

        assertThat(repository.findById(ownerTenant, worker.id()).toCompletableFuture().join())
                .contains(worker);
        assertThat(repository.findById(otherTenant, worker.id()).toCompletableFuture().join())
                .isEmpty();
        assertThat(repository.findById(ownerTenant, WorkerId.generate()).toCompletableFuture().join())
                .isEmpty();
        assertThat(repository.existsById(ownerTenant, worker.id()).toCompletableFuture().join()).isTrue();
        assertThat(repository.existsById(otherTenant, worker.id()).toCompletableFuture().join()).isFalse();
        assertThat(repository.existsById(ownerTenant, WorkerId.generate()).toCompletableFuture().join()).isFalse();
    }

    @Test
    void allowsSamePersonInDifferentTenants() {
        var repository = new InMemoryWorkerRepository();
        var person = person();
        var firstTenant = tenant();
        var secondTenant = tenant();
        var first = worker(firstTenant, person);
        var second = worker(secondTenant, person);

        repository.save(first).toCompletableFuture().join();
        repository.save(second).toCompletableFuture().join();

        assertThat(repository.findByPersonRef(firstTenant, person).toCompletableFuture().join())
                .contains(first);
        assertThat(repository.findByPersonRef(secondTenant, person).toCompletableFuture().join())
                .contains(second);
    }

    @Test
    void updatingWorkerIdentityRemovesOldIndex() {
        var repository = new InMemoryWorkerRepository();
        var tenant = tenant();
        var workerId = WorkerId.generate();
        var originalPerson = person();
        var replacementPerson = person();

        repository.save(worker(workerId, tenant, originalPerson)).toCompletableFuture().join();
        repository.save(worker(workerId, tenant, replacementPerson)).toCompletableFuture().join();

        assertThat(repository.findByPersonRef(tenant, originalPerson).toCompletableFuture().join()).isEmpty();
        assertThat(repository.findByPersonRef(tenant, replacementPerson).toCompletableFuture().join())
                .isPresent()
                .get()
                .extracting(Worker::id)
                .isEqualTo(workerId);
    }

    @Test
    void deleteUsesStoredIdentityRatherThanCallerSuppliedIdentity() {
        var repository = new InMemoryWorkerRepository();
        var tenant = tenant();
        var storedPerson = person();
        var otherPerson = person();
        var stored = worker(tenant, storedPerson);
        var other = worker(tenant, otherPerson);
        repository.save(stored).toCompletableFuture().join();
        repository.save(other).toCompletableFuture().join();

        // A stale object with stored's ID must not remove the other person's index.
        var stale = worker(stored.id(), tenant, otherPerson);
        repository.delete(stale).toCompletableFuture().join();

        assertThat(repository.findByPersonRef(tenant, storedPerson).toCompletableFuture().join()).isEmpty();
        assertThat(repository.findByPersonRef(tenant, otherPerson).toCompletableFuture().join())
                .contains(other);
    }

    private static Worker worker(TenantId tenant, PersonRef person) {
        return worker(WorkerId.generate(), tenant, person);
    }

    private static Worker worker(WorkerId id, TenantId tenant, PersonRef person) {
        return Worker.create(id, tenant, person, WorkerType.EMPLOYEE, "test", Instant.now());
    }

    private static TenantId tenant() {
        return TenantId.of(UUID.randomUUID());
    }

    private static PersonRef person() {
        return PersonRef.of(UUID.randomUUID());
    }
}
