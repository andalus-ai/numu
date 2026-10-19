package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CompletionStage;

/**
 * SPI port defining attendance policy rules that adapters implement.
 * Determines grace periods, shift tolerances, overtime thresholds, etc.
 */
public interface AttendancePolicy {

    /**
     * Returns the allowed late-arrival grace period (in seconds) for the given employment
     * on a given work date.
     */
    CompletionStage<Duration> graceperiodForLateArrival(WorkerId workerId, EmploymentId employmentId, LocalDate workDate);

    /**
     * Determines whether working beyond the scheduled shift end triggers overtime
     * for the given employment context.
     */
    CompletionStage<Boolean> isOvertimeEligible(WorkerId workerId, EmploymentId employmentId);

    /**
     * Returns the daily overtime threshold (duration after which overtime begins)
     * for the given employment.
     */
    CompletionStage<Duration> dailyOvertimeThreshold(WorkerId workerId, EmploymentId employmentId);

    /**
     * Validates whether a clock-in at the given instant is permitted within policy rules.
     * Returns an error message if denied, empty if allowed.
     */
    CompletionStage<java.util.Optional<String>> validateClockIn(WorkerId workerId, EmploymentId employmentId, Instant clockIn, LocalDate workDate);
}
