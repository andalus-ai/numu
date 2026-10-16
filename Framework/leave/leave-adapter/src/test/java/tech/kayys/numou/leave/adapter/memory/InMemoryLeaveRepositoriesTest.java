package tech.kayys.numou.leave.adapter.memory;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.leave.domain.LeaveRequest;
import tech.kayys.numou.leave.domain.LeaveRequestId;
import tech.kayys.numou.leave.domain.LeaveType;
import tech.kayys.numou.leave.domain.LeaveTypeId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLeaveRepositoriesTest {

    @Test
    void leaveType_saveAndFind() throws ExecutionException, InterruptedException {
        InMemoryLeaveTypeRepository repo = new InMemoryLeaveTypeRepository();
        LeaveType lt = LeaveType.create(
                LeaveTypeId.generate(), TenantId.generate(), "ANNUAL", "Annual Leave");
        repo.save(lt).toCompletableFuture().get();
        Optional<LeaveType> found = repo.findById(lt.getId()).toCompletableFuture().get();
        assertThat(found).isPresent();
        assertThat(found.get().getCode()).isEqualTo("ANNUAL");
    }

    @Test
    void leaveRequest_saveAndFind() throws ExecutionException, InterruptedException {
        InMemoryLeaveRequestRepository repo = new InMemoryLeaveRequestRepository();
        UUID subject = UUID.randomUUID();
        UUID context = UUID.randomUUID();
        LeaveRequest req = LeaveRequest.create(
                LeaveRequestId.generate(), subject, context, LeaveTypeId.generate(),
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 3, 14),
                BigDecimal.valueOf(5), "Vacation");
        repo.save(req).toCompletableFuture().get();
        Optional<LeaveRequest> found = repo.findById(req.getId()).toCompletableFuture().get();
        assertThat(found).isPresent();
        assertThat(found.get().getSubjectId()).isEqualTo(subject);
    }
}
