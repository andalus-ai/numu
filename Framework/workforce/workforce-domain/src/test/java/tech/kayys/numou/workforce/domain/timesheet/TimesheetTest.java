package tech.kayys.syirkah.workforce.domain.timesheet;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.workforce.domain.employment.EmploymentId;
import tech.kayys.syirkah.workforce.domain.timesheet.event.TimesheetApproved;
import tech.kayys.syirkah.workforce.domain.timesheet.event.TimesheetCreated;
import tech.kayys.syirkah.workforce.domain.timesheet.event.TimesheetEntryAdded;
import tech.kayys.syirkah.workforce.domain.timesheet.event.TimesheetRejected;
import tech.kayys.syirkah.workforce.domain.timesheet.event.TimesheetSubmitted;
import tech.kayys.syirkah.workforce.domain.worker.WorkerId;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class TimesheetTest {

    private static final WorkerId WORKER = WorkerId.generate();
    private static final EmploymentId EMPLOYMENT = EmploymentId.generate();
    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 30);

    @Test
    void create_raisesCreatedEvent() {
        Timesheet ts = Timesheet.create(TimesheetId.generate(), WORKER, EMPLOYMENT, START, END);
        assertThat(ts.getStatus()).isEqualTo(TimesheetStatus.DRAFT);
        assertThat(ts.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(TimesheetCreated.class);
    }

    @Test
    void addEntry_accumulatesDuration() {
        Timesheet ts = Timesheet.create(TimesheetId.generate(), WORKER, EMPLOYMENT, START, END);
        ts.pullDomainEvents();

        ts.addEntry(new TimesheetEntry(
                TimesheetEntryId.generate(),
                LocalDate.of(2026, 9, 10),
                Duration.ofHours(5),
                WorkContextRef.project("PROJ-101"),
                "Development"));

        ts.addEntry(new TimesheetEntry(
                TimesheetEntryId.generate(),
                LocalDate.of(2026, 9, 11),
                Duration.ofHours(3),
                WorkContextRef.workOrder("WO-402"),
                "Maintenance"));

        assertThat(ts.totalDuration()).isEqualTo(Duration.ofHours(8));
        assertThat(ts.getEntries()).hasSize(2);
        assertThat(ts.pullDomainEvents()).hasSize(2)
                .allMatch(e -> e instanceof TimesheetEntryAdded);
    }

    @Test
    void addEntry_outsidePeriod_throwsException() {
        Timesheet ts = Timesheet.create(TimesheetId.generate(), WORKER, EMPLOYMENT, START, END);

        assertThatThrownBy(() -> ts.addEntry(new TimesheetEntry(
                TimesheetEntryId.generate(),
                LocalDate.of(2026, 10, 1), // outside September
                Duration.ofHours(4),
                WorkContextRef.project("PROJ-1"),
                null
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lifecycle_draftToSubmittedToApproved() {
        Timesheet ts = Timesheet.create(TimesheetId.generate(), WORKER, EMPLOYMENT, START, END);
        ts.addEntry(new TimesheetEntry(
                TimesheetEntryId.generate(),
                LocalDate.of(2026, 9, 15),
                Duration.ofHours(8),
                WorkContextRef.project("PROJ-9"),
                "Full day"));
        ts.pullDomainEvents();

        ts.submit();
        assertThat(ts.getStatus()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(ts.pullDomainEvents()).hasSize(1).first().isInstanceOf(TimesheetSubmitted.class);

        ts.approve("supervisor@corp.com");
        assertThat(ts.getStatus()).isEqualTo(TimesheetStatus.APPROVED);
        assertThat(ts.getApprovedBy()).isEqualTo("supervisor@corp.com");
        assertThat(ts.pullDomainEvents()).hasSize(1).first().isInstanceOf(TimesheetApproved.class);
    }

    @Test
    void lifecycle_draftToSubmittedToRejected() {
        Timesheet ts = Timesheet.create(TimesheetId.generate(), WORKER, EMPLOYMENT, START, END);
        ts.submit();
        ts.pullDomainEvents();

        ts.reject("supervisor@corp.com", "Missing project descriptions");
        assertThat(ts.getStatus()).isEqualTo(TimesheetStatus.REJECTED);
        assertThat(ts.getRejectionReason()).isEqualTo("Missing project descriptions");
        assertThat(ts.pullDomainEvents()).hasSize(1).first().isInstanceOf(TimesheetRejected.class);
    }
}
