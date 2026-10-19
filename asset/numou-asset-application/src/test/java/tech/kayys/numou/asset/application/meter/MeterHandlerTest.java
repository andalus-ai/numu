package tech.kayys.numou.asset.application.meter;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.asset.domain.event.AssetMeterReadingRecorded;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.meter.*;
import tech.kayys.numou.asset.domain.model.Asset;
import tech.kayys.numou.asset.domain.repository.*;
import tech.kayys.numou.asset.domain.valueobject.AssetType;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
public class MeterHandlerTest {
  static class MemAssets implements AssetRepository {
    Map<String, Asset> m = new HashMap<>();
    public CompletionStage<Boolean> existsByAssetNumber(String t, String n) { return CompletableFuture.completedFuture(false); }
    public CompletionStage<Optional<Asset>> findByTenantAndId(String t, AssetId id) { return CompletableFuture.completedFuture(Optional.ofNullable(m.get(t + id.value()))); }
    public CompletionStage<Boolean> existsByTenantAndId(String t, AssetId id) { return CompletableFuture.completedFuture(m.containsKey(t + id.value())); }
    public CompletionStage<Void> deleteByTenantAndId(String t, AssetId id) { m.remove(t + id.value()); return CompletableFuture.completedFuture(null); }
    public CompletionStage<Asset> save(Asset a) { return CompletableFuture.completedFuture(a); }
    public CompletionStage<Optional<Asset>> findById(AssetId id) { return CompletableFuture.completedFuture(Optional.empty()); }
    public CompletionStage<Boolean> existsById(AssetId id) { return CompletableFuture.completedFuture(false); }
    public CompletionStage<Void> delete(Asset a) { return CompletableFuture.completedFuture(null); }
    public CompletionStage<Void> deleteById(AssetId id) { return CompletableFuture.completedFuture(null); }
  }
  static class MemMeters implements AssetMeterRepository {
    Map<String, AssetMeter> m = new HashMap<>();
    public CompletionStage<AssetMeter> save(String t, AssetMeter meter) { m.put(t + meter.id().value(), meter); return CompletableFuture.completedFuture(meter); }
    public CompletionStage<Optional<AssetMeter>> findById(String t, AssetMeterId id) { return CompletableFuture.completedFuture(Optional.ofNullable(m.get(t + id.value()))); }
    public CompletionStage<List<AssetMeter>> findByAssetId(String t, UUID assetId) { List<AssetMeter> out = new ArrayList<>(); for (AssetMeter x : m.values()) if (x.tenantId().equals(t) && x.assetId().equals(assetId)) out.add(x); return CompletableFuture.completedFuture(out); }
  }
  static class MemReadings implements MeterReadingRepository {
    List<MeterReading> all = new ArrayList<>();
    public CompletionStage<MeterReading> save(String t, MeterReading r) { all.add(r); return CompletableFuture.completedFuture(r); }
    public CompletionStage<Optional<MeterReading>> findBySourceRef(String t, String s, String ref) { return CompletableFuture.completedFuture(all.stream().filter(r -> r.tenantId().equals(t) && Objects.equals(r.source(), s) && Objects.equals(r.sourceRef(), ref)).findFirst()); }
    public CompletionStage<List<MeterReading>> findByMeterId(String t, UUID meterId) { List<MeterReading> out = new ArrayList<>(); for (MeterReading r : all) if (r.tenantId().equals(t) && r.meterId().equals(meterId)) out.add(r); out.sort(Comparator.comparing(MeterReading::recordedAt)); return CompletableFuture.completedFuture(out); }
    public CompletionStage<Optional<MeterReading>> findLatest(String t, UUID meterId) { return findByMeterId(t, meterId).thenApply(l -> l.isEmpty() ? Optional.empty() : Optional.of(l.get(l.size() - 1))); }
    public CompletionStage<Optional<MeterReading>> findPrevious(String t, UUID meterId, Instant at) { return findByMeterId(t, meterId).thenApply(l -> { MeterReading best = null; for (MeterReading r : l) if (r.recordedAt().isBefore(at) && (best == null || r.recordedAt().isAfter(best.recordedAt()))) best = r; return Optional.ofNullable(best); }); }
    public CompletionStage<Optional<MeterReading>> findNext(String t, UUID meterId, Instant at) { return findByMeterId(t, meterId).thenApply(l -> { MeterReading best = null; for (MeterReading r : l) if (r.recordedAt().isAfter(at) && (best == null || r.recordedAt().isBefore(best.recordedAt()))) best = r; return Optional.ofNullable(best); }); }
  }
  static class NoopPublisher implements EventPublisher { public Uni<Void> publish(List<DomainEvent> e) { return Uni.createFrom().nullItem(); } }
  static class DirectUow implements UnitOfWork { public <R> Uni<R> execute(Supplier<Uni<R>> w) { return w.get(); } }
  static class FixedClock implements DomainClock { private final Instant now = Instant.parse("2026-10-03T00:00:00Z"); public Instant now() { return now; } }
  private Asset asset(MemAssets assets, String tenant) {
    Asset a = Asset.create(AssetId.generate(), tenant, "A-" + UUID.randomUUID(), "Truck", AssetType.VEHICLE, new FixedClock());
    assets.m.put(tenant + a.id().value(), a);
    return a;
  }
  @Test void registerAndRecordMonotonic() {
    MemAssets assets = new MemAssets(); MemMeters meters = new MemMeters(); MemReadings readings = new MemReadings();
    Asset a = asset(assets, "t1");
    RegisterAssetMeterHandler reg = new RegisterAssetMeterHandler(assets, meters);
    Result<MeterResult> rr = reg.handle(new RegisterAssetMeterCommand("t1", a.id().value(), MeterType.ODOMETER, MeterUnit.KM, MeterBehavior.MONOTONIC, "Odo", null)).await().indefinitely();
    assertTrue(rr.isSuccess());
    RecordMeterReadingHandler rec = new RecordMeterReadingHandler(assets, meters, readings, new NoopPublisher(), new DirectUow(), new FixedClock());
    UUID meterId = rr.orElseThrow().meterId();
    Result<MeterReadingResult> r1 = rec.handle(new RecordMeterReadingCommand("t1", a.id().value(), meterId, new BigDecimal("100"), MeterUnit.KM, Instant.parse("2026-01-01T00:00:00Z"), "op", MeterReadingType.NORMAL, null, null)).await().indefinitely();
    assertTrue(r1.isSuccess());
    Result<MeterReadingResult> bad = rec.handle(new RecordMeterReadingCommand("t1", a.id().value(), meterId, new BigDecimal("90"), MeterUnit.KM, Instant.parse("2026-01-02T00:00:00Z"), "op", MeterReadingType.NORMAL, null, null)).await().indefinitely();
    assertTrue(bad.isFailure());
  }
  @Test void backdatedCorridorAndIdempotencyAndMileConversion() {
    MemAssets assets = new MemAssets(); MemMeters meters = new MemMeters(); MemReadings readings = new MemReadings();
    Asset a = asset(assets, "t1");
    RegisterAssetMeterHandler reg = new RegisterAssetMeterHandler(assets, meters);
    UUID meterId = reg.handle(new RegisterAssetMeterCommand("t1", a.id().value(), MeterType.ODOMETER, MeterUnit.KM, MeterBehavior.MONOTONIC, "Odo", null)).await().indefinitely().orElseThrow().meterId();
    RecordMeterReadingHandler rec = new RecordMeterReadingHandler(assets, meters, readings, new NoopPublisher(), new DirectUow(), new FixedClock());
    assertTrue(rec.handle(new RecordMeterReadingCommand("t1", a.id().value(), meterId, new BigDecimal("100"), MeterUnit.KM, Instant.parse("2026-01-01T00:00:00Z"), "op", MeterReadingType.NORMAL, null, null)).await().indefinitely().isSuccess());
    assertTrue(rec.handle(new RecordMeterReadingCommand("t1", a.id().value(), meterId, new BigDecimal("150"), MeterUnit.KM, Instant.parse("2026-01-10T00:00:00Z"), "op", MeterReadingType.NORMAL, null, null)).await().indefinitely().isSuccess());
    assertTrue(rec.handle(new RecordMeterReadingCommand("t1", a.id().value(), meterId, new BigDecimal("120"), MeterUnit.KM, Instant.parse("2026-01-05T00:00:00Z"), "op", MeterReadingType.NORMAL, null, null)).await().indefinitely().isSuccess());
    assertTrue(rec.handle(new RecordMeterReadingCommand("t1", a.id().value(), meterId, new BigDecimal("180"), MeterUnit.KM, Instant.parse("2026-01-06T00:00:00Z"), "op", MeterReadingType.NORMAL, null, null)).await().indefinitely().isFailure());
    Result<MeterReadingResult> first = rec.handle(new RecordMeterReadingCommand("t1", a.id().value(), meterId, new BigDecimal("160"), MeterUnit.KM, Instant.parse("2026-01-11T00:00:00Z"), "op", MeterReadingType.NORMAL, "DEVICE-A", "READING-123")).await().indefinitely();
    Result<MeterReadingResult> second = rec.handle(new RecordMeterReadingCommand("t1", a.id().value(), meterId, new BigDecimal("160"), MeterUnit.KM, Instant.parse("2026-01-11T00:00:00Z"), "op", MeterReadingType.NORMAL, "DEVICE-A", "READING-123")).await().indefinitely();
    assertTrue(first.isSuccess() && second.isSuccess());
    assertTrue(second.orElseThrow().duplicate());
    assertEquals(first.orElseThrow().readingId(), second.orElseThrow().readingId());
    Result<MeterReadingResult> miles = rec.handle(new RecordMeterReadingCommand("t1", a.id().value(), meterId, new BigDecimal("100"), MeterUnit.MILE, Instant.parse("2026-01-20T00:00:00Z"), "op", MeterReadingType.NORMAL, null, null)).await().indefinitely();
    assertTrue(miles.isSuccess());
    assertEquals(0, new BigDecimal("160.9344").compareTo(miles.orElseThrow().value()));
    GetMeterUsageHandler usage = new GetMeterUsageHandler(meters, readings);
    MeterUsageResult u = usage.handle(new GetMeterUsageQuery("t1", meterId, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-21T00:00:00Z"))).await().indefinitely();
    assertTrue(u.usage().compareTo(BigDecimal.ZERO) > 0);
  }
}
