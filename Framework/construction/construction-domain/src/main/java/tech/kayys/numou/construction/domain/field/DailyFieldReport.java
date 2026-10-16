package tech.kayys.syirkah.construction.domain.field;

import tech.kayys.syirkah.construction.domain.field.event.DailyFieldReportSubmitted;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class DailyFieldReport extends AbstractAggregateRoot<DailyFieldReportId> {
    private final UUID siteId;
    private final LocalDate reportDate;
    private final String reportNumber;
    private String weatherSummary;
    private String remarks;
    private DailyFieldReportStatus status;

    private DailyFieldReport(DailyFieldReportId id, UUID siteId, LocalDate reportDate, String reportNumber) {
        super(id);
        this.siteId = Objects.requireNonNull(siteId);
        this.reportDate = Objects.requireNonNull(reportDate);
        this.reportNumber = Objects.requireNonNull(reportNumber);
        this.status = DailyFieldReportStatus.DRAFT;
    }

    public static DailyFieldReport create(UUID siteId, LocalDate reportDate, String reportNumber) {
        return new DailyFieldReport(DailyFieldReportId.generate(), siteId, reportDate, reportNumber);
    }

    public void setWeather(String weatherSummary) {
        this.weatherSummary = weatherSummary;
    }

    public void submit() {
        this.status = DailyFieldReportStatus.SUBMITTED;
        raise(new DailyFieldReportSubmitted(UUID.randomUUID(), Instant.now(), id().value(), siteId, reportDate));
    }

    public void approve() {
        this.status = DailyFieldReportStatus.APPROVED;
    }

    public UUID siteId() { return siteId; }
    public LocalDate reportDate() { return reportDate; }
    public String reportNumber() { return reportNumber; }
    public String weatherSummary() { return weatherSummary; }
    public String remarks() { return remarks; }
    public DailyFieldReportStatus status() { return status; }
}
