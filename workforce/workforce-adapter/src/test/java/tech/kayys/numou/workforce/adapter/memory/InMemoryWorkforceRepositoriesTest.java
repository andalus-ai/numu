package tech.kayys.numou.workforce.adapter.memory;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workforce.domain.employment.Employment;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.employment.EmploymentType;
import tech.kayys.numou.workforce.domain.employment.OrganizationRef;
import tech.kayys.numou.foundation.domain.ref.PersonRef;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.skill.Skill;
import tech.kayys.numou.workforce.domain.skill.SkillId;
import tech.kayys.numou.workforce.domain.worker.Worker;
import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.domain.worker.WorkerType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryWorkforceRepositoriesTest {

    @Test
    void shouldSaveAndFindWorker() {
        var repo = new InMemoryWorkerRepository();
        var workerId = WorkerId.generate();
        var tenantId = TenantId.of(UUID.randomUUID());
        var personRef = PersonRef.of(UUID.randomUUID());

        var worker = Worker.create(workerId, tenantId, personRef, WorkerType.EMPLOYEE, "admin", Instant.now());
        repo.save(worker).toCompletableFuture().join();

        var foundById = repo.findById(workerId).toCompletableFuture().join();
        assertThat(foundById).isPresent();
        assertThat(foundById.get().id()).isEqualTo(workerId);

        var foundByPerson = repo.findByPersonRef(tenantId, personRef).toCompletableFuture().join();
        assertThat(foundByPerson).isPresent();
        assertThat(foundByPerson.get().id()).isEqualTo(workerId);

        repo.deleteById(workerId).toCompletableFuture().join();
        assertThat(repo.findById(workerId).toCompletableFuture().join()).isEmpty();
    }

    @Test
    void shouldSaveAndQueryEmployment() {
        var repo = new InMemoryEmploymentRepository();
        var workerId = WorkerId.generate();
        var orgRef = OrganizationRef.of(UUID.randomUUID());
        var employment = Employment.start(
                EmploymentId.generate(),
                workerId,
                orgRef,
                null,
                EmploymentType.PERMANENT,
                LocalDate.of(2026, 1, 1)
        );

        repo.save(employment).toCompletableFuture().join();

        var list = repo.findByWorkerId(workerId).toCompletableFuture().join();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).id()).isEqualTo(employment.id());
    }

    @Test
    void shouldSaveAndFindSkillByCode() {
        var repo = new InMemorySkillRepository();
        var skill = Skill.create(SkillId.generate(), "KOTLIN", "Kotlin", "Modern JVM language", null);
        repo.save(skill).toCompletableFuture().join();

        var found = repo.findByCode("KOTLIN").toCompletableFuture().join();
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Kotlin");
    }
}
