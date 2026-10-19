package tech.kayys.numou.asset.application.meter;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.meter.AssetMeter;
import tech.kayys.numou.asset.domain.meter.AssetMeterId;
import tech.kayys.numou.asset.domain.meter.MeterReading;
import tech.kayys.numou.asset.domain.repository.AssetMeterRepository;
import tech.kayys.numou.asset.domain.repository.MeterReadingRepository;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
public class GetMeterUsageHandler {
  private final AssetMeterRepository meters;
  private final MeterReadingRepository readings;
  public GetMeterUsageHandler(AssetMeterRepository m, MeterReadingRepository r) { this.meters = Objects.requireNonNull(m); this.readings = Objects.requireNonNull(r); }
  public Uni<MeterUsageResult> handle(GetMeterUsageQuery q) {
    return Uni.createFrom().completionStage(() -> meters.findById(q.tenantId(), AssetMeterId.of(q.meterId()))).flatMap(mo -> {
      AssetMeter meter = mo.orElseThrow(() -> new ApplicationErrorException(ApplicationError.of("meter.not-found", "Meter not found")));
      return Uni.createFrom().completionStage(() -> readings.findByMeterId(q.tenantId(), q.meterId())).map(all -> {
        List<MeterReading> window = new ArrayList<>();
        for (MeterReading r : all) {
          if (q.from() != null && r.recordedAt().isBefore(q.from())) continue;
          if (q.to() != null && !r.recordedAt().isAfter(q.to())) window.add(r);
          else if (q.to() == null) window.add(r);
        }
        if (window.isEmpty()) return new MeterUsageResult(meter.id().value(), meter.unit().name(), null, null, BigDecimal.ZERO, 0);
        BigDecimal start = window.get(0).value();
        BigDecimal end = window.get(window.size() - 1).value();
        BigDecimal usage = meter.isMonotonic() ? end.subtract(start) : null;
        return new MeterUsageResult(meter.id().value(), meter.unit().name(), start, end, usage, window.size());
      });
    });
  }
}
