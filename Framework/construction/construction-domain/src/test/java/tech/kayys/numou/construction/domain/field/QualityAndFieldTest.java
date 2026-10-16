package tech.kayys.syirkah.construction.domain.field;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.construction.domain.quality.*;

import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class QualityAndFieldTest {
    @Test
    void shouldHandleInspectionAndNcr() {
        var siteId = UUID.randomUUID();
        var wbsId = UUID.randomUUID();

        var inspection = InspectionRequest.request(siteId, wbsId, LocalDate.now(), "Level 3 Column B2");
        assertThat(inspection.status()).isEqualTo(InspectionRequestStatus.REQUESTED);

        inspection.completeInspection(QualityInspectionResult.FAIL);
        assertThat(inspection.status()).isEqualTo(InspectionRequestStatus.INSPECTED);
        assertThat(inspection.result()).isEqualTo(QualityInspectionResult.FAIL);

        var ncr = NonConformanceReport.create(siteId, "NCR-2026-001", "Honeycombing detected on column B2", NcrSeverity.MAJOR);
        assertThat(ncr.status()).isEqualTo(NcrStatus.OPEN);

        ncr.proposeCorrectiveAction("Chipping and epoxy pressure grouting");
        assertThat(ncr.status()).isEqualTo(NcrStatus.CORRECTIVE_ACTION_PENDING);

        ncr.close();
        assertThat(ncr.status()).isEqualTo(NcrStatus.CLOSED);
    }

    @Test
    void shouldCreateDailyFieldReport() {
        var siteId = UUID.randomUUID();
        var report = DailyFieldReport.create(siteId, LocalDate.now(), "DFR-2026-09-30");

        assertThat(report.status()).isEqualTo(DailyFieldReportStatus.DRAFT);
        report.setWeather("Sunny morning, heavy rain in afternoon (14:00 - 16:00)");
        report.submit();
        assertThat(report.status()).isEqualTo(DailyFieldReportStatus.SUBMITTED);
        report.approve();
        assertThat(report.status()).isEqualTo(DailyFieldReportStatus.APPROVED);

        var events = report.pullDomainEvents();
        assertThat(events).hasSize(1);
    }
}
