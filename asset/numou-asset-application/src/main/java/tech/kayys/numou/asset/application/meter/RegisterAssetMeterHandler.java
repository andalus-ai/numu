package tech.kayys.numou.asset.application.meter;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.meter.AssetMeter;
import tech.kayys.numou.asset.domain.meter.AssetMeterId;
import tech.kayys.numou.asset.domain.meter.MeterBehavior;
import tech.kayys.numou.asset.domain.repository.AssetMeterRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;

/** Registers a meter definition after verifying the asset exists in the same tenant. */
public class RegisterAssetMeterHandler implements CommandHandler<RegisterAssetMeterCommand, Result<MeterResult>> {

    private final AssetRepository assets;
    private final AssetMeterRepository meters;

    public RegisterAssetMeterHandler(AssetRepository assets, AssetMeterRepository meters) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.meters = Objects.requireNonNull(meters, "meters");
    }

    @Override
    public Uni<Result<MeterResult>> handle(RegisterAssetMeterCommand command) {
        if (command.tenantId() == null || command.tenantId().isBlank()) {
            return Uni.createFrom().item(Result.failure(
                    ApplicationError.of("meter.tenant-required", "Tenant is required")));
        }
        if (command.assetId() == null || command.type() == null || command.unit() == null) {
            return Uni.createFrom().item(Result.failure(
                    ApplicationError.of("meter.invalid-argument", "assetId, type and unit are required")));
        }
        if (command.name() == null || command.name().isBlank()) {
            return Uni.createFrom().item(Result.failure(
                    ApplicationError.of("meter.invalid-argument", "Meter name is required")));
        }
        MeterBehavior behavior = command.behavior() == null ? MeterBehavior.MONOTONIC : command.behavior();
        return Uni.createFrom()
                .completionStage(() -> assets.findByTenantAndId(command.tenantId(), AssetId.of(command.assetId())))
                .flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return Uni.createFrom().<Result<MeterResult>>item(Result.failure(
                                ApplicationError.of("asset.not-found", "Asset not found")));
                    }
                    AssetMeter meter = AssetMeter.register(AssetMeterId.generate(), command.tenantId(),
                            command.assetId(), command.type(), command.unit(), behavior,
                            command.name(), command.replacementOf());
                    return Uni.createFrom()
                            .completionStage(() -> meters.save(command.tenantId(), meter))
                            .map(saved -> Result.success(MeterResult.from(saved)))
                            .onFailure(ApplicationErrorException.class)
                            .recoverWithItem(e -> Result.failure(((ApplicationErrorException) e).error()));
                });
    }
}
