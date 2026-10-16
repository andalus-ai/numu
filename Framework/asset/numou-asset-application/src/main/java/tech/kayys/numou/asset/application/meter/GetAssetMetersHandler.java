package tech.kayys.numou.asset.application.meter;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.meter.MeterReading;
import tech.kayys.numou.asset.domain.repository.AssetMeterRepository;
import tech.kayys.numou.asset.domain.repository.MeterReadingRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
public class GetAssetMetersHandler {
  private final AssetMeterRepository meters;
  private final MeterReadingRepository readings;
  public GetAssetMetersHandler(AssetMeterRepository m, MeterReadingRepository r) { this.meters = Objects.requireNonNull(m); this.readings = Objects.requireNonNull(r); }
  public Uni<List<MeterSnapshot>> handle(GetAssetMetersQuery q) {
    return Uni.createFrom().completionStage(() -> meters.findByAssetId(q.tenantId(), q.assetId())).flatMap(list -> {
      if (list.isEmpty()) return Uni.createFrom().item(List.of());
      Uni<List<MeterSnapshot>> acc = Uni.createFrom().item(new ArrayList<>());
      for (var meter : list) {
        acc = acc.flatMap(out -> Uni.createFrom().completionStage(() -> readings.findLatest(q.tenantId(), meter.id().value())).map(latest -> { out.add(new MeterSnapshot(meter.id().value(), meter.assetId(), meter.type().name(), meter.unit().name(), latest.map(MeterReading::value).orElse(null), latest.map(MeterReading::recordedAt).orElse(null))); return out; }));
      }
      return acc;
    });
  }
}
