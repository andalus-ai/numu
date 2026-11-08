package tech.kayys.numou.history.source.domain;
import java.util.Objects;
import java.util.UUID;
public record HistoricalClaimId(UUID value) {
 public HistoricalClaimId { Objects.requireNonNull(value, "claim id is required"); }
 public static HistoricalClaimId generate() { return new HistoricalClaimId(UUID.randomUUID()); }
}