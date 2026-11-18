package tech.kayys.numou.history.source.domain;
import java.util.Objects;
import java.util.UUID;
public record HistoricalSourceId(UUID value) {
 public HistoricalSourceId { Objects.requireNonNull(value, "source id is required"); }
 public static HistoricalSourceId generate() { return new HistoricalSourceId(UUID.randomUUID()); }
}