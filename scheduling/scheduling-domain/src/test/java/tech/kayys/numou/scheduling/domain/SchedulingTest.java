package tech.kayys.numou.scheduling.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.ref.ResourceRef;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.scheduling.domain.event.ScheduleCancelled;
import tech.kayys.numou.scheduling.domain.event.ScheduleCreated;
import tech.kayys.numou.scheduling.domain.event.SchedulePublished;
import tech.kayys.numou.scheduling.domain.event.ShiftCancelled;
import tech.kayys.numou.scheduling.domain.event.ShiftCompleted;
import tech.kayys.numou.scheduling.domain.event.ShiftScheduled;
import tech.kayys.numou.scheduling.domain.event.ShiftStarted;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class SchedulingTest {

    private static final TenantId TENANT = TenantId.generate();

    @Test
    void createWorkPattern_raisesCreatedEvent() {
        WorkPattern wp = WorkPattern.create(
                WorkPatternId.generate(), TENANT, "Standard 9-5", WorkPatternType.FIXED);
        assertThat(wp.isActive()).isTrue();
        assertThat(wp.pullDomainEvents()).hasSize(1);
    }

    @Test
    void createSchedule_raisesCreatedEvent() {
        Schedule s = Schedule.create(ScheduleId.generate(), TENANT, "Week 1",
                LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 12));
        assertThat(s.isDraft()).isTrue();
        assertThat(s.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(ScheduleCreated.class);
    }

    @Test
    void publishSchedule_transitionsToPublished() {
        Schedule s = buildDraftSchedule();
        s.publish();
        assertThat(s.isPublished()).isTrue();
        assertThat(s.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(SchedulePublished.class);
    }

    @Test
    void cancelSchedule_transitionsToCancelled() {
        Schedule s = buildDraftSchedule();
        s.publish();
        s.pullDomainEvents();
        s.cancel();
        assertThat(s.getStatus()).isEqualTo(ScheduleStatus.CANCELLED);
        assertThat(s.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(ScheduleCancelled.class);
    }

    @Test
    void scheduleShift_withWorkerResource_raisesShiftScheduledEvent() {
        ResourceRef worker = ResourceRef.ofWorker(UUID.randomUUID());
        Shift shift = Shift.schedule(ShiftId.generate(), ScheduleId.generate(), worker,
                LocalDateTime.of(2025, 1, 6, 9, 0),
                LocalDateTime.of(2025, 1, 6, 17, 0),
                "Office");
        assertThat(shift.getStatus()).isEqualTo(ShiftStatus.SCHEDULED);
        assertThat(shift.getAssignee().resourceType()).isEqualTo("WORKER");
        assertThat(shift.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(ShiftScheduled.class);
    }

    @Test
    void shiftLifecycle_scheduledStartedCompleted() {
        ResourceRef worker = ResourceRef.ofWorker(UUID.randomUUID());
        Shift shift = Shift.schedule(ShiftId.generate(), ScheduleId.generate(), worker,
                LocalDateTime.of(2025, 1, 6, 9, 0),
                LocalDateTime.of(2025, 1, 6, 17, 0), null);
        shift.pullDomainEvents();

        shift.start();
        assertThat(shift.getStatus()).isEqualTo(ShiftStatus.STARTED);
        assertThat(shift.pullDomainEvents()).hasSize(1).first().isInstanceOf(ShiftStarted.class);

        shift.complete();
        assertThat(shift.getStatus()).isEqualTo(ShiftStatus.COMPLETED);
        assertThat(shift.pullDomainEvents()).hasSize(1).first().isInstanceOf(ShiftCompleted.class);
    }

    @Test
    void cancelShift_raisesShiftCancelledEvent() {
        ResourceRef worker = ResourceRef.ofWorker(UUID.randomUUID());
        Shift shift = Shift.schedule(ShiftId.generate(), ScheduleId.generate(), worker,
                LocalDateTime.of(2025, 1, 6, 9, 0),
                LocalDateTime.of(2025, 1, 6, 17, 0), null);
        shift.pullDomainEvents();
        shift.cancel();
        assertThat(shift.getStatus()).isEqualTo(ShiftStatus.CANCELLED);
        assertThat(shift.pullDomainEvents()).hasSize(1).first().isInstanceOf(ShiftCancelled.class);
    }

    @Test
    void resourceAvailability_workerAndVehicle_areDistinct() {
        ResourceRef worker = ResourceRef.ofWorker(UUID.randomUUID());
        ResourceRef vehicle = ResourceRef.ofVehicle(UUID.randomUUID());

        ResourceAvailability wa = ResourceAvailability.declare(
                ResourceAvailabilityId.generate(), worker,
                LocalDate.of(2025, 1, 6),
                LocalTime.of(9, 0), LocalTime.of(17, 0),
                AvailabilityStatus.AVAILABLE);

        ResourceAvailability va = ResourceAvailability.declare(
                ResourceAvailabilityId.generate(), vehicle,
                LocalDate.of(2025, 1, 6),
                LocalTime.of(8, 0), LocalTime.of(18, 0),
                AvailabilityStatus.AVAILABLE);

        assertThat(wa.getResource().resourceType()).isEqualTo("WORKER");
        assertThat(va.getResource().resourceType()).isEqualTo("VEHICLE");
        assertThat(wa.isAvailable()).isTrue();
    }

    private Schedule buildDraftSchedule() {
        Schedule s = Schedule.create(ScheduleId.generate(), TENANT, "Test Schedule",
                LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 12));
        s.pullDomainEvents();
        return s;
    }
}
