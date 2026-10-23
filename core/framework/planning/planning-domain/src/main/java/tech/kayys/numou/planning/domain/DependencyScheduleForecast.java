package tech.kayys.numou.planning.domain;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
/** Immutable critical-path calculation for a neutral activity dependency network. */
public record DependencyScheduleForecast(LocalDate projectStart, LocalDate forecastFinish, int projectDurationDays, List<ActivityId> criticalPath, Map<ActivityId, ActivityScheduleForecast> activities, String methodVersion) {
 public DependencyScheduleForecast {
  Objects.requireNonNull(projectStart); Objects.requireNonNull(forecastFinish);
  criticalPath=List.copyOf(Objects.requireNonNull(criticalPath)); activities=Map.copyOf(Objects.requireNonNull(activities));
  methodVersion=Objects.requireNonNull(methodVersion).trim();
  if(methodVersion.isEmpty()) throw new IllegalArgumentException("Method version cannot be blank");
  if(projectDurationDays<0||!projectStart.plusDays(projectDurationDays).equals(forecastFinish)) throw new IllegalArgumentException("Invalid project duration/finish");
  if(activities.isEmpty()||criticalPath.isEmpty()) throw new IllegalArgumentException("Activities and critical path cannot be empty");
 }
}
