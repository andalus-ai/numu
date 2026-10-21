package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.school.domain.FeeInvoiceId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordFeePaymentCommand(TenantId tenantId, FeeInvoiceId invoiceId, BigDecimal amount, LocalDate paidOn, String method) {}
