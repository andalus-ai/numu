package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record IssueFeeInvoiceCommand(TenantId tenantId, StudentId studentId, YearMonth period, BigDecimal amount, LocalDate dueDate) {}
