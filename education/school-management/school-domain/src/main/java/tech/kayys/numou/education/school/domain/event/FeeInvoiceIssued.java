package tech.kayys.numou.education.school.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.school.domain.FeeInvoiceId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record FeeInvoiceIssued(
        UUID eventId,
        Instant occurredAt,
        FeeInvoiceId invoiceId,
        TenantId tenantId,
        StudentId studentId,
        YearMonth period,
        BigDecimal amount
) implements DomainEvent {
    public FeeInvoiceIssued(FeeInvoiceId invoiceId, TenantId tenantId, StudentId studentId, YearMonth period, BigDecimal amount) {
        this(UUID.randomUUID(), Instant.now(), invoiceId, tenantId, studentId, period, amount);
    }

    @Override
    public String eventType() {
        return "school.fee.invoice-issued";
    }
}
