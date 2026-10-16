package tech.kayys.numou.scheduling.adapter.memory;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.ref.ResourceRef;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.scheduling.domain.Schedule;
import tech.kayys.numou.scheduling.domain.ScheduleId;
import tech.kayys.numou.scheduling.domain.Shift;
import tech.kayys.numou.scheduling.domain.ShiftId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySchedulingRepositoriesTest {

    @Test
    void schedule_saveAndFind() throws ExecutionException, InterruptedException {
        InMemoryScheduleRepository repo = new InMemoryScheduleRepository();
        Schedule s = Schedule.create(ScheduleId.generate(), TenantId.generate(), "Week 1",
                LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 12));
        repo.save(s).toCompletableFuture().get();
        Optional<Schedule> found = repo.findById(s.getId()).toCompletableFuture().get();
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Week 1");
    }

    @Test
    void shift_saveAndFind() throws ExecutionException, InterruptedException {
        InMemoryShiftRepository repo = new InMemoryShiftRepository();
        ResourceRef worker = ResourceRef.ofWorker(UUID.randomUUID());
        Shift shift = Shift.schedule(ShiftId.generate(), ScheduleId.generate(), worker,
                LocalDateTime.of(2025, 1, 6, 9, 0),
                LocalDateTime.of(2025, 1, 6, 17, 0), "Office");
        repo.save(shift).toCompletableFuture().get();
        Optional<Shift> found = repo.findById(shift.getId()).toCompletableFuture().get();
        assertThat(found).isPresent();
        assertThat(found.get().getAssignee().resourceType()).isEqualTo("WORKER");
    }
}
