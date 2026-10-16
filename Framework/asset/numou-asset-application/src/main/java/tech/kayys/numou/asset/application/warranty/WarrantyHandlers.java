package tech.kayys.numou.asset.application.warranty;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.meter.MeterType;
import tech.kayys.numou.asset.domain.meter.MeterUnit;
import tech.kayys.numou.asset.domain.repository.AssetWarrantyRepository;
import tech.kayys.numou.asset.domain.warranty.AssetWarranty;
import tech.kayys.numou.asset.domain.warranty.AssetWarrantyId;
import tech.kayys.numou.asset.domain.warranty.WarrantyCoverage;
import tech.kayys.numou.asset.domain.warranty.WarrantyExclusion;
import tech.kayys.numou.asset.domain.warranty.WarrantyExpiryRule;
import tech.kayys.numou.asset.domain.warranty.WarrantyType;
import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Warranty registration + lifecycle (ASSET-23). */
public final class WarrantyHandlers {
    private WarrantyHandlers() {}

    public record RegisterAssetWarrantyCommand(
            String tenantId, String assetId, String warrantyNumber, String providerId, String providerName,
            WarrantyType type, WarrantyExpiryRule expiryRule, Instant startsAt, Instant expiresAt,
            MeterType meterType, BigDecimal meterLimit, MeterUnit meterUnit,
            List<WarrantyCoverage> coverages, List<WarrantyExclusion> exclusions) implements Command {}

    public static final class Register extends AbstractWarrantyCommandHandler
            implements CommandHandler<RegisterAssetWarrantyCommand, Result<String>> {
        private final AssetWarrantyRepository warranties;

        public Register(AssetWarrantyRepository warranties, EventPublisher eventPublisher,
                        UnitOfWork unitOfWork, DomainClock clock) {
            super(eventPublisher, unitOfWork, clock);
            this.warranties = Objects.requireNonNull(warranties, "warranties");
        }

        @Override
        public Uni<Result<String>> handle(RegisterAssetWarrantyCommand command) {
            return Uni.createFrom()
                    .completionStage(() -> warranties.existsByTenantAndWarrantyNumber(
                            command.tenantId(), command.warrantyNumber()))
                    .flatMap(exists -> {
                        if (Boolean.TRUE.equals(exists)) {
                            return Uni.createFrom().item(Result.<String>failure(ApplicationError.of(
                                    "warranty.number.duplicate",
                                    "Warranty number already exists: " + command.warrantyNumber())));
                        }
                        AssetWarranty warranty = AssetWarranty.register(AssetWarrantyId.generate(),
                                command.tenantId(), UUID.fromString(command.assetId()), command.warrantyNumber(),
                                command.providerId(), command.providerName(), command.type(), command.expiryRule(),
                                command.startsAt(), command.expiresAt(), command.meterType(), command.meterLimit(),
                                command.meterUnit(), command.coverages(), command.exclusions(), clock);
                        return saveAndPublish(warranties.save(warranty), warranty)
                                .map(saved -> Result.success(saved.id().value().toString()));
                    });
        }
    }

    public record ActivateWarrantyCommand(String tenantId, String warrantyId) implements Command {}
    public record ExpireWarrantyCommand(String tenantId, String warrantyId) implements Command {}
    public record CancelWarrantyCommand(String tenantId, String warrantyId) implements Command {}

    abstract static class Lifecycle<C extends Command> extends AbstractWarrantyCommandHandler {
        protected final AssetWarrantyRepository warranties;
        protected final BiConsumer<AssetWarranty, DomainClock> transition;

        Lifecycle(AssetWarrantyRepository warranties, EventPublisher eventPublisher,
                  UnitOfWork unitOfWork, DomainClock clock,
                  BiConsumer<AssetWarranty, DomainClock> transition) {
            super(eventPublisher, unitOfWork, clock);
            this.warranties = Objects.requireNonNull(warranties, "warranties");
            this.transition = Objects.requireNonNull(transition, "transition");
        }

        protected Uni<Result<String>> apply(String tenantId, String warrantyId) {
            AssetWarrantyId id = AssetWarrantyId.of(UUID.fromString(warrantyId));
            return Uni.createFrom().completionStage(() -> warranties.findByTenantAndId(tenantId, id))
                    .flatMap(opt -> {
                        AssetWarranty warranty = opt.orElseThrow(() -> new ApplicationErrorException(
                                ApplicationError.of("warranty.not-found", "Warranty not found: " + warrantyId)));
                        transition.accept(warranty, clock);
                        return saveAndPublish(warranties.save(warranty), warranty)
                                .map(saved -> Result.success(saved.id().value().toString()));
                    });
        }
    }

    public static final class Activate extends Lifecycle<ActivateWarrantyCommand>
            implements CommandHandler<ActivateWarrantyCommand, Result<String>> {
        public Activate(AssetWarrantyRepository warranties, EventPublisher eventPublisher,
                        UnitOfWork unitOfWork, DomainClock clock) {
            super(warranties, eventPublisher, unitOfWork, clock, AssetWarranty::activate);
        }
        @Override public Uni<Result<String>> handle(ActivateWarrantyCommand command) {
            return apply(command.tenantId(), command.warrantyId());
        }
    }

    public static final class Expire extends Lifecycle<ExpireWarrantyCommand>
            implements CommandHandler<ExpireWarrantyCommand, Result<String>> {
        public Expire(AssetWarrantyRepository warranties, EventPublisher eventPublisher,
                      UnitOfWork unitOfWork, DomainClock clock) {
            super(warranties, eventPublisher, unitOfWork, clock, AssetWarranty::expire);
        }
        @Override public Uni<Result<String>> handle(ExpireWarrantyCommand command) {
            return apply(command.tenantId(), command.warrantyId());
        }
    }

    public static final class Cancel extends Lifecycle<CancelWarrantyCommand>
            implements CommandHandler<CancelWarrantyCommand, Result<String>> {
        public Cancel(AssetWarrantyRepository warranties, EventPublisher eventPublisher,
                      UnitOfWork unitOfWork, DomainClock clock) {
            super(warranties, eventPublisher, unitOfWork, clock, AssetWarranty::cancel);
        }
        @Override public Uni<Result<String>> handle(CancelWarrantyCommand command) {
            return apply(command.tenantId(), command.warrantyId());
        }
    }
}
