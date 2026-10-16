package tech.kayys.numou.asset.application.meter;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.meter.MeterReading;
import tech.kayys.numou.asset.domain.repository.MeterReadingRepository;
import java.util.List;
import java.util.Objects;
public class GetMeterReadingsHandler {
  private final MeterReadingRepository readings;
  public GetMeterReadingsHandler(MeterReadingRepository r) { this.readings = Objects.requireNonNull(r); }
  public Uni<List<MeterReading>> handle(GetMeterReadingsQuery q) {
    return Uni.createFrom().completionStage(() -> readings.findByMeterId(q.tenantId(), q.meterId()));
  }
}
