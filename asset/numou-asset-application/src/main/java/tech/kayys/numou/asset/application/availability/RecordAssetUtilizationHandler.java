package tech.kayys.numou.asset.application.availability;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.availability.AssetUtilizationRecord;
import tech.kayys.numou.asset.domain.availability.AssetUtilizationRecordId;
import tech.kayys.numou.asset.domain.event.AssetUtilizationRecorded;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.asset.domain.repository.AssetUtilizationRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Records a utilization observation (ASSET-26 §15).
 *
 * <p>Idempotent for keyed external ingestion: a repeat of the same
 * {@code source}/{@code referenceId} returns the stored record flagged as a
 * duplicate rather than failing or double-counting.</p>
 */
public class RecordAssetUtilizationHandler
        implements CommandHandler<RecordAssetUtilizationCommand, Result<UtilizationResult>> {

    private final AssetRepository assets;
    private final AssetUtilizationRepository utilization;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;

    public RecordAssetUtilizationHandler(
            AssetRepository assets,
            AssetUtilizationRepository utilization,
            EventPublisher eventPublisher,
            UnitOfWork unitOfWork,
            DomainClock clock) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.utilization = Objects.requireNonNull(utilization, "utilization");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Uni<Result<UtilizationResult>> handle(RecordAssetUtilizationCommand cmd) {
        if (cmd.tenantId() == null || cmd.tenantId().isBlank()) {
            return failure("utilization.tenant-required", "Tenant is required");
        }
        if (cmd.assetId() == null || cmd.startsAt() == null || cmd.endsAt() == null
                || cmd.type() == null || cmd.quantity() == null) {
            return failure("utilization.invalid-argument",
                    "assetId, startsAt, endsAt, type and quantity are required");
        }
        if (!cmd.endsAt().isAfter(cmd.startsAt())) {
            return failure("utilization.invalid-interval", "endsAt must be after startsAt");
        }
        return keyed(cmd).flatMap(cached -> cached != null
                ? Uni.createFrom().item(Result.success(cached))
                : persist(cmd));
    }

    private Uni<UtilizationResult> keyed(RecordAssetUtilizationCommand cmd) {
        if (cmd.source() == null || cmd.source().isBlank()
                || cmd.referenceId() == null || cmd.referenceId().isBlank()) {
            return Uni.createFrom().nullItem();
        }
        return Uni.createFrom()
                .completionStage(() -> utilization.findBySourceRef(
                        cmd.tenantId(), cmd.source(), cmd.referenceId()))
                .map(opt -> opt.map(r -> UtilizationResult.from(r, true)).orElse(null));
    }

    private Uni<Result<UtilizationResult>> persist(RecordAssetUtilizationCommand cmd) {
        return Uni.createFrom()
                .completionStage(() -> assets.findByTenantAndId(cmd.tenantId(), AssetId.of(cmd.assetId())))
                .flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return failure("asset.not-found", "Asset not found");
                    }
                    AssetUtilizationRecord record = AssetUtilizationRecord.record(
                            AssetUtilizationRecordId.generate(), cmd.tenantId(), cmd.assetId(),
                            cmd.startsAt(), cmd.endsAt(), cmd.type(), cmd.quantity(), cmd.unit(),
                            cmd.source(), cmd.referenceId(), clock.now());
                    AssetUtilizationRecorded event = new AssetUtilizationRecorded(
                            UUID.randomUUID(), clock.now(), cmd.assetId(), record.id().value(),
                            record.type(), record.quantity(), record.unit(),
                            record.startsAt(), record.endsAt());
                    return unitOfWork.execute(() -> Uni.createFrom()
                                    .completionStage(() -> utilization.save(cmd.tenantId(), record))
                                    .flatMap(saved -> eventPublisher.publish(List.of(event)).replaceWith(saved)))
                            .map(saved -> (Result<UtilizationResult>)
                                    Result.success(UtilizationResult.from(saved, false)))
                            .onFailure().recoverWithItem(RecordAssetUtilizationHandler::asFailure);
                });
    }

    private static Uni<Result<UtilizationResult>> failure(String code, String message) {
        return Uni.createFrom().item(Result.failure(ApplicationError.of(code, message)));
    }

    private static Result<UtilizationResult> asFailure(Throwable failure) {
        if (failure instanceof ApplicationErrorException app) {
            return Result.failure(app.error());
        }
        return Result.failure(ApplicationError.of("utilization.unexpected",
                failure.getMessage() == null ? "Unexpected error" : failure.getMessage()));
    }
}