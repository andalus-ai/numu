package tech.kayys.numou.leave.application.command;

import tech.kayys.numou.leave.domain.LeaveTypeId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SubmitLeaveRequestCommand(
        UUID subjectId,
        UUID contextId,
        LeaveTypeId leaveTypeId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal amount,
        String reason
) {}
