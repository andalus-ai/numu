package tech.kayys.numou.asset.application.availability;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityPeriod;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityPeriodId;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityReason;
import tech.kayys.numou.asset.domain.event.AssetAvailabilityMarked;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.repository.AssetAvailabilityRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Records an availability / unavailability window (ASSET-26 §10).
 *
 * <p>Flow: validate tenant -&gt; load asset -&gt; check lifecycle -&gt; validate
 * interval -&gt; check same-type overlap -&gt; persist -&gt; publish event. All
 * inside the {@link UnitOfWork} so the period write and the transactional-outbox
 * insert commit atomically (matching ASSET-11).</p>
 */
public class MarkAssetAvailabilityHandler
        implements CommandHandler<MarkAssetAvailabilityCommand, Result<AvailabilityResult>> {

    private final AssetRepository assets;
    private final AssetAvailabilityRepository availability;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;

    public MarkAssetAvailabilityHandler(
            AssetRepository assets,
            AssetAvailabilityRepository availability,
            EventPublisher eventPublisher,
            UnitOfWork unitOfWork,
            DomainClock clock) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.availability = Objects.requireNonNull(availability, "availability");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Uni<Result<AvailabilityResult>> handle(MarkAssetAvailabilityCommand cmd) {
        if (cmd.tenantId() == null || cmd.tenantId().isBlank()) {
            return failure("availability.tenant-required", "Tenant is required");
        }
        if (cmd.assetId() == null || cmd.type() == null) {
            return failure("availability.invalid-argument", "assetId and type are required");
        }
        Instant startsAt = cmd.startsAt() == null ? clock.now() : cmd.startsAt();
        Instant endsAt = cmd.endsAt();
        if (endsAt != null && !endsAt.isAfter(startsAt)) {
            return failure("availability.invalid-interval", "endsAt must be after startsAt");
        }
        AssetAvailabilityReason reason = cmd.reason() == null
                ? AssetAvailabilityReason.OTHER : cmd.reason();

        return Uni.createFrom()
                .completionStage(() -> assets.findByTenantAndId(cmd.tenantId(), AssetId.of(cmd.assetId())))
                .flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return failure("asset.not-found", "Asset not found");
                    }
                    if (opt.get().status().isTerminal()) {
                        return failure("availability.asset-disposed",
                                "A disposed asset cannot participate in availability");
                    }
                    return record(cmd, startsAt, endsAt, reason);
                })
                .onFailure().recoverWithItem(MarkAssetAvailabilityHandler::asFailure);
    }

    private Uni<Result<AvailabilityResult>> record(
            MarkAssetAvailabilityCommand cmd, Instant startsAt, Instant endsAt, AssetAvailabilityReason reason) {
        return Uni.createFrom()
                .completionStage(() -> availability.hasOverlap(
                        cmd.tenantId(), cmd.assetId(), cmd.type(), startsAt, endsAt))
                .flatMap(overlap -> {
                    if (Boolean.TRUE.equals(overlap)) {
                        return failure("availability.overlap",
                                "An overlapping " + cmd.type() + " period already exists");
                    }
                    AssetAvailabilityPeriod period = AssetAvailabilityPeriod.mark(
                            AssetAvailabilityPeriodId.generate(), cmd.tenantId(), cmd.assetId(),
                            startsAt, endsAt, cmd.type(), reason, cmd.referenceId(), cmd.notes());
                    AssetAvailabilityMarked event = new AssetAvailabilityMarked(
                            UUID.randomUUID(), clock.now(), cmd.assetId(), period.id().value(),
                            period.type(), period.reason(), period.startsAt(), period.endsAt(),
                            period.referenceId());
                    return unitOfWork.execute(() -> Uni.createFrom()
                                    .completionStage(() -> availability.save(cmd.tenantId(), period))
                                    .flatMap(saved -> eventPublisher.publish(List.of(event)).replaceWith(saved)))
                            .map(saved -> (Result<AvailabilityResult>) Result.success(AvailabilityResult.from(saved)));
                });
    }

    private static Uni<Result<AvailabilityResult>> failure(String code, String message) {
        return Uni.createFrom().item(Result.failure(ApplicationError.of(code, message)));
    }

    private static Result<AvailabilityResult> asFailure(Throwable failure) {
        if (failure instanceof ApplicationErrorException app) {
            return Result.failure(app.error());
        }
        return Result.failure(ApplicationError.of("availability.unexpected",
                failure.getMessage() == null ? "Unexpected error" : failure.getMessage()));
    }
}