package tech.kayys.numou.leave.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.leave.domain.event.LeaveBalanceAllocated;
import tech.kayys.numou.leave.domain.event.LeaveRequestApproved;
import tech.kayys.numou.leave.domain.event.LeaveRequestCreated;
import tech.kayys.numou.leave.domain.event.LeaveRequestRejected;
import tech.kayys.numou.leave.domain.event.LeaveTypeCreated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class LeaveTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID SUBJECT = UUID.randomUUID();
    private static final UUID CONTEXT = UUID.randomUUID();

    @Test
    void createLeaveType_raisesCreatedEvent() {
        LeaveType lt = LeaveType.create(LeaveTypeId.generate(), TENANT, "ANNUAL", "Annual Leave");
        assertThat(lt.isActive()).isTrue();
        assertThat(lt.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(LeaveTypeCreated.class);
    }

    @Test
    void deactivateLeaveType_transitionsToInactive() {
        LeaveType lt = LeaveType.create(LeaveTypeId.generate(), TENANT, "SICK", "Sick Leave");
        lt.pullDomainEvents();
        lt.deactivate();
        assertThat(lt.isActive()).isFalse();
    }

    @Test
    void allocateLeaveBalance_raisesAllocatedEvent() {
        LeaveBalance b = LeaveBalance.allocate(
                LeaveBalanceId.generate(), SUBJECT, CONTEXT,
                LeaveTypeId.generate(),
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31),
                BigDecimal.valueOf(14));
        assertThat(b.available()).isEqualByComparingTo("14");
        assertThat(b.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(LeaveBalanceAllocated.class);
    }

    @Test
    void reserve_reducesAvailable() {
        LeaveBalance b = buildBalance(BigDecimal.valueOf(10));
        b.reserve(BigDecimal.valueOf(3));
        assertThat(b.available()).isEqualByComparingTo("7");
    }

    @Test
    void reserve_insufficientBalance_throwsException() {
        LeaveBalance b = buildBalance(BigDecimal.valueOf(2));
        assertThatThrownBy(() -> b.reserve(BigDecimal.valueOf(5)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void consume_afterReserve_reducesReservation() {
        LeaveBalance b = buildBalance(BigDecimal.valueOf(10));
        b.reserve(BigDecimal.valueOf(5));
        b.consume(BigDecimal.valueOf(5));
        assertThat(b.getConsumed()).isEqualByComparingTo("5");
        assertThat(b.getReserved()).isEqualByComparingTo("0");
        assertThat(b.available()).isEqualByComparingTo("5");
    }

    @Test
    void leaveRequest_fullLifecycle_approvalPath() {
        LeaveTypeId typeId = LeaveTypeId.generate();
        LeaveRequest req = LeaveRequest.create(
                LeaveRequestId.generate(), SUBJECT, CONTEXT, typeId,
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 3, 14),
                BigDecimal.valueOf(5), "Annual vacation");
        assertThat(req.getStatus()).isEqualTo(LeaveRequestStatus.DRAFT);
        req.pullDomainEvents();

        req.submit();
        assertThat(req.getStatus()).isEqualTo(LeaveRequestStatus.SUBMITTED);
        req.pullDomainEvents();

        req.approve("manager@company.com");
        assertThat(req.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(req.getApprovedBy()).isEqualTo("manager@company.com");
        assertThat(req.pullDomainEvents()).hasSize(1).first().isInstanceOf(LeaveRequestApproved.class);
    }

    @Test
    void leaveRequest_fullLifecycle_rejectionPath() {
        LeaveRequest req = buildDraftRequest();
        req.submit();
        req.pullDomainEvents();
        req.reject("hr@company.com", "Insufficient balance");
        assertThat(req.getStatus()).isEqualTo(LeaveRequestStatus.REJECTED);
        assertThat(req.pullDomainEvents()).hasSize(1).first().isInstanceOf(LeaveRequestRejected.class);
    }

    @Test
    void leaveRequest_cancelDraftRequest() {
        LeaveRequest req = buildDraftRequest();
        req.cancel("Changed plans");
        assertThat(req.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
    }

    @Test
    void createLeaveRequest_raisesCreatedEvent() {
        LeaveRequest req = LeaveRequest.create(
                LeaveRequestId.generate(), SUBJECT, CONTEXT, LeaveTypeId.generate(),
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 3, 14),
                BigDecimal.valueOf(5), null);
        assertThat(req.pullDomainEvents()).hasSize(1).first().isInstanceOf(LeaveRequestCreated.class);
    }

    private LeaveBalance buildBalance(BigDecimal entitlement) {
        LeaveBalance b = LeaveBalance.allocate(
                LeaveBalanceId.generate(), SUBJECT, CONTEXT, LeaveTypeId.generate(),
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), entitlement);
        b.pullDomainEvents();
        return b;
    }

    private LeaveRequest buildDraftRequest() {
        LeaveRequest req = LeaveRequest.create(
                LeaveRequestId.generate(), SUBJECT, CONTEXT, LeaveTypeId.generate(),
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 3, 14),
                BigDecimal.valueOf(5), null);
        req.pullDomainEvents();
        return req;
    }
}
