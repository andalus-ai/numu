package tech.kayys.numou.planning.domain;
import java.time.LocalDate;
import java.util.Objects;
/** Earliest/latest dates and total float for one activity. */
public record ActivityScheduleForecast(ActivityId activityId, LocalDate earliestStart, LocalDate earliestFinish, LocalDate latestStart, LocalDate latestFinish, int totalFloatDays, boolean critical) {
 public ActivityScheduleForecast {
  Objects.requireNonNull(activityId); Objects.requireNonNull(earliestStart); Objects.requireNonNull(earliestFinish); Objects.requireNonNull(latestStart); Objects.requireNonNull(latestFinish);
  if(totalFloatDays<0) throw new IllegalArgumentException("Total float cannot be negative");
  if(critical!=(totalFloatDays==0)) throw new IllegalArgumentException("Critical flag must match zero total float");
  if(earliestFinish.isBefore(earliestStart)||latestFinish.isBefore(latestStart)) throw new IllegalArgumentException("Finish date cannot precede start date");
 }
}
