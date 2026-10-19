package tech.kayys.numou.asset.infrastructure.persistence;
import io.quarkus.hibernate.reactive.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.meter.MeterReading;
import tech.kayys.numou.asset.domain.meter.MeterReadingId;
import tech.kayys.numou.asset.domain.repository.MeterReadingRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
@ApplicationScoped
public class MeterReadingRepositoryAdapter implements MeterReadingRepository {
  @Override
  public CompletionStage<MeterReading> save(String tenantId, MeterReading reading) {
    MeterReadingEntity e = toEntity(reading);
    return Panache.withTransaction(() -> Panache.getSession().flatMap(s -> s.persist(e)).replaceWith(reading)).subscribe().asCompletionStage();
  }
  @Override
  public CompletionStage<Optional<MeterReading>> findBySourceRef(String tenantId, String source, String sourceRef) {
    if (source == null || sourceRef == null) return java.util.concurrent.CompletableFuture.completedFuture(Optional.empty());
    return Panache.withSession(() -> MeterReadingEntity.<MeterReadingEntity>find("tenantId = ?1 and source = ?2 and sourceRef = ?3", tenantId, source, sourceRef).firstResult().map(e -> e == null ? Optional.<MeterReading>empty() : Optional.of(toDomain(e)))).subscribe().asCompletionStage();
  }
  @Override
  public CompletionStage<List<MeterReading>> findByMeterId(String tenantId, UUID meterId) {
    return Panache.withSession(() -> MeterReadingEntity.<MeterReadingEntity>list("tenantId = ?1 and meterId = ?2 order by recordedAt asc, id asc", tenantId, meterId).map(es -> es.stream().map(MeterReadingRepositoryAdapter::toDomain).collect(Collectors.toList()))).subscribe().asCompletionStage();
  }
  @Override
  public CompletionStage<Optional<MeterReading>> findLatest(String tenantId, UUID meterId) {
    return Panache.withSession(() -> MeterReadingEntity.<MeterReadingEntity>find("tenantId = ?1 and meterId = ?2 order by recordedAt desc, id desc", tenantId, meterId).firstResult().map(e -> e == null ? Optional.<MeterReading>empty() : Optional.of(toDomain(e)))).subscribe().asCompletionStage();
  }
  @Override
  public CompletionStage<Optional<MeterReading>> findPrevious(String tenantId, UUID meterId, Instant recordedAt) {
    return Panache.withSession(() -> MeterReadingEntity.<MeterReadingEntity>find("tenantId = ?1 and meterId = ?2 and recordedAt < ?3 order by recordedAt desc, id desc", tenantId, meterId, recordedAt).firstResult().map(e -> e == null ? Optional.<MeterReading>empty() : Optional.of(toDomain(e)))).subscribe().asCompletionStage();
  }
  @Override
  public CompletionStage<Optional<MeterReading>> findNext(String tenantId, UUID meterId, Instant recordedAt) {
    return Panache.withSession(() -> MeterReadingEntity.<MeterReadingEntity>find("tenantId = ?1 and meterId = ?2 and recordedAt > ?3 order by recordedAt asc, id asc", tenantId, meterId, recordedAt).firstResult().map(e -> e == null ? Optional.<MeterReading>empty() : Optional.of(toDomain(e)))).subscribe().asCompletionStage();
  }
  static MeterReadingEntity toEntity(MeterReading r) {
    MeterReadingEntity e = new MeterReadingEntity();
    e.id = r.id().value(); e.tenantId = r.tenantId(); e.meterId = r.meterId(); e.assetId = r.assetId();
    e.value = r.value(); e.unit = r.unit(); e.recordedAt = r.recordedAt(); e.occurredAt = r.occurredAt();
    e.recordedBy = r.recordedBy(); e.readingType = r.type(); e.source = r.source(); e.sourceRef = r.sourceRef();
    e.createdAt = r.occurredAt(); return e;
  }
  static MeterReading toDomain(MeterReadingEntity e) {
    return new MeterReading(MeterReadingId.of(e.id), e.tenantId, e.meterId, e.assetId, e.value, e.unit, e.recordedAt, e.occurredAt, e.recordedBy, e.readingType, e.source, e.sourceRef);
  }
}
