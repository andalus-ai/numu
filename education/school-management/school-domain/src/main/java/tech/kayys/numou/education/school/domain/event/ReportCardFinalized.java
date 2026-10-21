package tech.kayys.numou.education.school.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.core.AcademicYear;
import tech.kayys.numou.education.core.Semester;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.school.domain.ReportCardId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record ReportCardFinalized(
        UUID eventId,
        Instant occurredAt,
        ReportCardId reportCardId,
        TenantId tenantId,
        StudentId studentId,
        AcademicYear academicYear,
        Semester semester
) implements DomainEvent {
    public ReportCardFinalized(ReportCardId reportCardId, TenantId tenantId, StudentId studentId, AcademicYear academicYear, Semester semester) {
        this(UUID.randomUUID(), Instant.now(), reportCardId, tenantId, studentId, academicYear, semester);
    }

    @Override
    public String eventType() {
        return "school.report-card.finalized";
    }
}
