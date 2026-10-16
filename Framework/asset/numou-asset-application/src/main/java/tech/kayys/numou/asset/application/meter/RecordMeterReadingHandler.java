package tech.kayys.numou.asset.application.meter;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.event.AssetMeterReadingRecorded;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.meter.AssetMeter;
import tech.kayys.numou.asset.domain.meter.AssetMeterId;
import tech.kayys.numou.asset.domain.meter.MeterReading;
import tech.kayys.numou.asset.domain.meter.MeterReadingId;
import tech.kayys.numou.asset.domain.meter.MeterReadingType;
import tech.kayys.numou.asset.domain.repository.AssetMeterRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.asset.domain.repository.MeterReadingRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class RecordMeterReadingHandler implements CommandHandler<RecordMeterReadingCommand, Result<MeterReadingResult>> {
  private final AssetRepository assets;
  private final AssetMeterRepository meters;
  private final MeterReadingRepository readings;
  private final EventPublisher eventPublisher;
  private final UnitOfWork unitOfWork;
  private final DomainClock clock;
  public RecordMeterReadingHandler(AssetRepository a, AssetMeterRepository m, MeterReadingRepository r, EventPublisher e, UnitOfWork u, DomainClock c) {
    this.assets = Objects.requireNonNull(a); this.meters = Objects.requireNonNull(m);
    this.readings = Objects.requireNonNull(r); this.eventPublisher = Objects.requireNonNull(e);
    this.unitOfWork = Objects.requireNonNull(u); this.clock = Objects.requireNonNull(c);
  }
  @Override
  public Uni<Result<MeterReadingResult>> handle(RecordMeterReadingCommand cmd) {
    if (cmd.tenantId() == null || cmd.tenantId().isBlank()) return Uni.createFrom().item(Result.failure(ApplicationError.of("meter.tenant-required", "Tenant is required")));
    if (cmd.value() == null) return Uni.createFrom().item(Result.failure(ApplicationError.of("meter.invalid-argument", "Reading value is required")));
    if (cmd.meterId() == null || cmd.assetId() == null || cmd.unit() == null) return Uni.createFrom().item(Result.failure(ApplicationError.of("meter.invalid-argument", "meterId, assetId and unit are required")));
    boolean keyed = cmd.source() != null && !cmd.source().isBlank() && cmd.sourceRef() != null && !cmd.sourceRef().isBlank();
    Uni<Result<MeterReadingResult>> cached = keyed
      ? Uni.createFrom().completionStage(() -> readings.findBySourceRef(cmd.tenantId(), cmd.source(), cmd.sourceRef())).map(e -> e.map(x -> Result.success(MeterReadingResult.from(x, true))).orElse(null))
      : Uni.createFrom().nullItem();
    return cached.flatMap(hit -> hit != null ? Uni.createFrom().item(hit) : record(cmd))
      .onFailure(BusinessRuleViolation.class).recoverWithItem(e -> Result.failure(ApplicationError.of("meter.rule-violation", e.getMessage())));
  }
  private Uni<Result<MeterReadingResult>> record(RecordMeterReadingCommand cmd) {
    return Uni.createFrom().completionStage(() -> assets.findByTenantAndId(cmd.tenantId(), AssetId.of(cmd.assetId()))).flatMap(asset -> {
      if (asset.isEmpty()) return Uni.createFrom().item(Result.<MeterReadingResult>failure(ApplicationError.of("asset.not-found", "Asset not found")));
      return Uni.createFrom().completionStage(() -> meters.findById(cmd.tenantId(), AssetMeterId.of(cmd.meterId()))).flatMap(mo -> {
        if (mo.isEmpty()) return Uni.createFrom().item(Result.<MeterReadingResult>failure(ApplicationError.of("meter.not-found", "Meter not found")));
        AssetMeter meter = mo.get();
        if (!meter.assetId().equals(cmd.assetId())) return Uni.createFrom().item(Result.<MeterReadingResult>failure(ApplicationError.of("meter.asset-mismatch", "Meter does not belong to asset")));
        BigDecimal canonical;
        try { canonical = MeterUnitConverter.toCanonical(cmd.value(), cmd.unit(), meter.unit()); }
        catch (IllegalArgumentException e) { return Uni.createFrom().item(Result.<MeterReadingResult>failure(ApplicationError.of("meter.unit-mismatch", e.getMessage()))); }
        Instant recordedAt = cmd.recordedAt() != null ? cmd.recordedAt() : clock.now();
        return checkCorridor(cmd.tenantId(), meter, canonical, recordedAt).flatMap(bad -> {
          if (bad != null) return Uni.createFrom().item(bad);
          MeterReadingType t = cmd.type() == null ? MeterReadingType.NORMAL : cmd.type();
          MeterReading reading = MeterReading.record(meter, MeterReadingId.generate(), canonical, meter.unit(), recordedAt, clock.now(), cmd.recordedBy(), t, cmd.source(), cmd.sourceRef());
          return unitOfWork.execute(() -> Uni.createFrom().completionStage(() -> readings.save(cmd.tenantId(), reading)).flatMap(saved -> eventPublisher.publish(List.of(new AssetMeterReadingRecorded(UUID.randomUUID(), clock.now(), saved.meterId(), saved.assetId(), saved.value(), saved.unit(), saved.recordedAt()))).replaceWith(Result.success(MeterReadingResult.from(saved, false)))));
        });
      });
    });
  }
  private Uni<Result<MeterReadingResult>> checkCorridor(String tenantId, AssetMeter meter, BigDecimal canonical, Instant recordedAt) {
    if (!meter.isMonotonic()) return Uni.createFrom().nullItem();
    return Uni.createFrom().completionStage(() -> readings.findPrevious(tenantId, meter.id().value(), recordedAt))
      .flatMap(prev -> Uni.createFrom().completionStage(() -> readings.findNext(tenantId, meter.id().value(), recordedAt)).map(next -> {
        try { MeterReading.validateCorridor(meter, canonical, prev, next); return null; }
        catch (BusinessRuleViolation e) { return Result.<MeterReadingResult>failure(ApplicationError.of("meter.monotonic-violation", e.getMessage())); }
      }));
  }
}
