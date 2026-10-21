package tech.kayys.numou.education.school.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.school.domain.FeeInvoiceId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record FeePaymentRecorded(
        UUID eventId,
        Instant occurredAt,
        FeeInvoiceId invoiceId,
        TenantId tenantId,
        StudentId studentId,
        BigDecimal amount,
        boolean fullyPaid
) implements DomainEvent {
    public FeePaymentRecorded(FeeInvoiceId invoiceId, TenantId tenantId, StudentId studentId, BigDecimal amount, boolean fullyPaid) {
        this(UUID.randomUUID(), Instant.now(), invoiceId, tenantId, studentId, amount, fullyPaid);
    }

    @Override
    public String eventType() {
        return "school.fee.payment-recorded";
    }
}
