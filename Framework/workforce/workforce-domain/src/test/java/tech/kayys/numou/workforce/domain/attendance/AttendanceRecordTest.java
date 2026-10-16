package tech.kayys.syirkah.workforce.domain.attendance;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.workforce.domain.attendance.event.AttendanceRecordClosed;
import tech.kayys.syirkah.workforce.domain.attendance.event.AttendanceRecordCreated;
import tech.kayys.syirkah.workforce.domain.attendance.event.WorkerClockedIn;
import tech.kayys.syirkah.workforce.domain.attendance.event.WorkerClockedOut;
import tech.kayys.syirkah.workforce.domain.employment.EmploymentId;
import tech.kayys.syirkah.workforce.domain.worker.WorkerId;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class AttendanceRecordTest {

    private static final WorkerId WORKER = WorkerId.generate();
    private static final EmploymentId EMPLOYMENT = EmploymentId.generate();
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    @Test
    void create_raisesCreatedEvent() {
        AttendanceRecord record = AttendanceRecord.create(
                AttendanceRecordId.generate(), WORKER, EMPLOYMENT, TODAY);

        assertThat(record.getStatus()).isEqualTo(AttendanceStatus.OPEN);
        assertThat(record.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(AttendanceRecordCreated.class);
    }

    @Test
    void clockInAndOut_calculatesDuration() {
        AttendanceRecord record = AttendanceRecord.create(
                AttendanceRecordId.generate(), WORKER, EMPLOYMENT, TODAY);
        record.pullDomainEvents();

        LocalDateTime start = TODAY.atTime(9, 0);
        LocalDateTime end = TODAY.atTime(17, 0);

        record.clockIn(start, AttendanceSource.BIOMETRIC);
        assertThat(record.hasOpenSession()).isTrue();
        assertThat(record.pullDomainEvents()).hasSize(1).first().isInstanceOf(WorkerClockedIn.class);

        record.clockOut(end);
        assertThat(record.hasOpenSession()).isFalse();
        assertThat(record.totalDuration()).isEqualTo(Duration.ofHours(8));
        assertThat(record.pullDomainEvents()).hasSize(1).first().isInstanceOf(WorkerClockedOut.class);
    }

    @Test
    void multipleSessions_sumTotalDuration() {
        AttendanceRecord record = AttendanceRecord.create(
                AttendanceRecordId.generate(), WORKER, EMPLOYMENT, TODAY);

        // Morning session: 08:00 - 12:00 (4 hours)
        record.clockIn(TODAY.atTime(8, 0));
        record.clockOut(TODAY.atTime(12, 0));

        // Afternoon session: 13:00 - 17:00 (4 hours)
        record.clockIn(TODAY.atTime(13, 0));
        record.clockOut(TODAY.atTime(17, 0));

        assertThat(record.totalDuration()).isEqualTo(Duration.ofHours(8));
        assertThat(record.getSessions()).hasSize(2);
    }

    @Test
    void clockIn_whenAlreadyOpen_throwsException() {
        AttendanceRecord record = AttendanceRecord.create(
                AttendanceRecordId.generate(), WORKER, EMPLOYMENT, TODAY);
        record.clockIn(TODAY.atTime(9, 0));

        assertThatThrownBy(() -> record.clockIn(TODAY.atTime(10, 0)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void close_withOpenSession_throwsException() {
        AttendanceRecord record = AttendanceRecord.create(
                AttendanceRecordId.generate(), WORKER, EMPLOYMENT, TODAY);
        record.clockIn(TODAY.atTime(9, 0));

        assertThatThrownBy(record::close)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void close_whenAllSessionsClosed_succeeds() {
        AttendanceRecord record = AttendanceRecord.create(
                AttendanceRecordId.generate(), WORKER, EMPLOYMENT, TODAY);
        record.clockIn(TODAY.atTime(9, 0));
        record.clockOut(TODAY.atTime(17, 0));
        record.pullDomainEvents();

        record.close();
        assertThat(record.getStatus()).isEqualTo(AttendanceStatus.CLOSED);
        assertThat(record.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(AttendanceRecordClosed.class);
    }
}
