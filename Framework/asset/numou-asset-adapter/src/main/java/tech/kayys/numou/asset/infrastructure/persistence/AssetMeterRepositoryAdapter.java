package tech.kayys.numou.asset.infrastructure.persistence;
import io.quarkus.hibernate.reactive.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.meter.AssetMeter;
import tech.kayys.numou.asset.domain.meter.AssetMeterId;
import tech.kayys.numou.asset.domain.repository.AssetMeterRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
@ApplicationScoped
public class AssetMeterRepositoryAdapter implements AssetMeterRepository {
  @Override
  public CompletionStage<AssetMeter> save(String tenantId, AssetMeter meter) {
    AssetMeterEntity e = toEntity(meter);
    return Panache.withTransaction(() -> Panache.getSession().flatMap(s -> s.<AssetMeterEntity>merge(e)).replaceWith(meter)).subscribe().asCompletionStage();
  }
  @Override
  public CompletionStage<Optional<AssetMeter>> findById(String tenantId, AssetMeterId meterId) {
    return Panache.withSession(() -> AssetMeterEntity.<AssetMeterEntity>find("tenantId = ?1 and id = ?2", tenantId, meterId.value()).firstResult().map(e -> e == null ? Optional.<AssetMeter>empty() : Optional.of(toDomain(e)))).subscribe().asCompletionStage();
  }
  @Override
  public CompletionStage<List<AssetMeter>> findByAssetId(String tenantId, UUID assetId) {
    return Panache.withSession(() -> AssetMeterEntity.<AssetMeterEntity>list("tenantId = ?1 and assetId = ?2 order by id asc", tenantId, assetId).map(es -> es.stream().map(AssetMeterRepositoryAdapter::toDomain).collect(Collectors.toList()))).subscribe().asCompletionStage();
  }
  static AssetMeterEntity toEntity(AssetMeter m) {
    AssetMeterEntity e = new AssetMeterEntity();
    e.id = m.id().value(); e.tenantId = m.tenantId(); e.assetId = m.assetId();
    e.meterType = m.type(); e.unit = m.unit(); e.behavior = m.behavior();
    e.name = m.name(); e.replacementOf = m.replacementOf(); e.active = m.active();
    e.createdAt = Instant.now(); return e;
  }
  static AssetMeter toDomain(AssetMeterEntity e) {
    return AssetMeter.of(AssetMeterId.of(e.id), e.tenantId, e.assetId, e.meterType, e.unit, e.behavior, e.name, e.replacementOf, e.active);
  }
}
