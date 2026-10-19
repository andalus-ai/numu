package tech.kayys.numou.asset.application.warranty;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.repository.AssetWarrantyRepository;
import tech.kayys.numou.asset.domain.repository.WarrantyClaimRepository;
import tech.kayys.numou.asset.domain.warranty.AssetWarrantyId;
import tech.kayys.numou.asset.domain.warranty.WarrantyClaim;
import tech.kayys.numou.asset.domain.warranty.WarrantyClaimId;
import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.Objects;
import java.util.UUID;

/** Claim filing + review lifecycle (ASSET-23 §§14-15). */
public final class ClaimHandlers {
    private ClaimHandlers() {}

    public record FileWarrantyClaimCommand(
            String tenantId, String warrantyId, String assetId, String claimNumber,
            String description, String submittedBy) implements Command {}

    public static final class File extends AbstractWarrantyCommandHandler
            implements CommandHandler<FileWarrantyClaimCommand, Result<String>> {
        private final WarrantyClaimRepository claims;
        private final AssetWarrantyRepository warranties;

        public File(WarrantyClaimRepository claims, AssetWarrantyRepository warranties,
                    EventPublisher eventPublisher, UnitOfWork unitOfWork, DomainClock clock) {
            super(eventPublisher, unitOfWork, clock);
            this.claims = Objects.requireNonNull(claims, "claims");
            this.warranties = Objects.requireNonNull(warranties, "warranties");
        }

        @Override
        public Uni<Result<String>> handle(FileWarrantyClaimCommand command) {
            AssetWarrantyId warrantyId = AssetWarrantyId.of(UUID.fromString(command.warrantyId()));
            return Uni.createFrom()
                    .completionStage(() -> warranties.findByTenantAndId(command.tenantId(), warrantyId))
                    .flatMap(warrantyOpt -> {
                        if (warrantyOpt.isEmpty()) {
                            return Uni.createFrom().item(Result.<String>failure(ApplicationError.of(
                                    "warranty.not-found", "Warranty not found: " + command.warrantyId())));
                        }
                        return Uni.createFrom()
                                .completionStage(() -> claims.existsByTenantAndClaimNumber(
                                        command.tenantId(), command.claimNumber()))
                                .flatMap(exists -> {
                                    if (Boolean.TRUE.equals(exists)) {
                                        return Uni.createFrom().item(Result.<String>failure(ApplicationError.of(
                                                "warranty.claim.duplicate",
                                                "Claim number already exists: " + command.claimNumber())));
                                    }
                                    WarrantyClaim claim = WarrantyClaim.file(WarrantyClaimId.generate(),
                                            command.tenantId(), warrantyId, UUID.fromString(command.assetId()),
                                            command.claimNumber(), command.description(), command.submittedBy(), clock);
                                    claim.submit(clock);
                                    return saveAndPublish(claims.save(claim), claim)
                                            .map(saved -> Result.success(saved.id().value().toString()));
                                });
                    });
        }
    }

    public record ReviewWarrantyClaimCommand(String tenantId, String claimId, boolean approved, String workOrderId)
            implements Command {}
    public record CompleteWarrantyClaimCommand(String tenantId, String claimId) implements Command {}
    public record CancelWarrantyClaimCommand(String tenantId, String claimId) implements Command {}

    public static final class Review extends AbstractWarrantyCommandHandler
            implements CommandHandler<ReviewWarrantyClaimCommand, Result<String>> {
        private final WarrantyClaimRepository claims;

        public Review(WarrantyClaimRepository claims, EventPublisher eventPublisher,
                      UnitOfWork unitOfWork, DomainClock clock) {
            super(eventPublisher, unitOfWork, clock);
            this.claims = Objects.requireNonNull(claims, "claims");
        }

        @Override
        public Uni<Result<String>> handle(ReviewWarrantyClaimCommand command) {
            WarrantyClaimId id = WarrantyClaimId.of(UUID.fromString(command.claimId()));
            return Uni.createFrom().completionStage(() -> claims.findByTenantAndId(command.tenantId(), id))
                    .flatMap(opt -> {
                        WarrantyClaim claim = opt.orElseThrow(() -> new ApplicationErrorException(
                                ApplicationError.of("warranty.claim.not-found", "Claim not found: " + command.claimId())));
                        if (command.approved()) {
                            UUID workOrderId = command.workOrderId() == null ? null
                                    : UUID.fromString(command.workOrderId());
                            claim.approve(workOrderId, clock);
                        } else {
                            claim.reject(clock);
                        }
                        return saveAndPublish(claims.save(claim), claim)
                                .map(saved -> Result.success(saved.id().value().toString()));
                    });
        }
    }

    public static final class Complete extends AbstractWarrantyCommandHandler
            implements CommandHandler<CompleteWarrantyClaimCommand, Result<String>> {
        private final WarrantyClaimRepository claims;

        public Complete(WarrantyClaimRepository claims, EventPublisher eventPublisher,
                        UnitOfWork unitOfWork, DomainClock clock) {
            super(eventPublisher, unitOfWork, clock);
            this.claims = Objects.requireNonNull(claims, "claims");
        }

        @Override
        public Uni<Result<String>> handle(CompleteWarrantyClaimCommand command) {
            WarrantyClaimId id = WarrantyClaimId.of(UUID.fromString(command.claimId()));
            return Uni.createFrom().completionStage(() -> claims.findByTenantAndId(command.tenantId(), id))
                    .flatMap(opt -> {
                        WarrantyClaim claim = opt.orElseThrow(() -> new ApplicationErrorException(
                                ApplicationError.of("warranty.claim.not-found", "Claim not found: " + command.claimId())));
                        claim.complete(clock);
                        return saveAndPublish(claims.save(claim), claim)
                                .map(saved -> Result.success(saved.id().value().toString()));
                    });
        }
    }

    public static final class Cancel extends AbstractWarrantyCommandHandler
            implements CommandHandler<CancelWarrantyClaimCommand, Result<String>> {
        private final WarrantyClaimRepository claims;

        public Cancel(WarrantyClaimRepository claims, EventPublisher eventPublisher,
                      UnitOfWork unitOfWork, DomainClock clock) {
            super(eventPublisher, unitOfWork, clock);
            this.claims = Objects.requireNonNull(claims, "claims");
        }

        @Override
        public Uni<Result<String>> handle(CancelWarrantyClaimCommand command) {
            WarrantyClaimId id = WarrantyClaimId.of(UUID.fromString(command.claimId()));
            return Uni.createFrom().completionStage(() -> claims.findByTenantAndId(command.tenantId(), id))
                    .flatMap(opt -> {
                        WarrantyClaim claim = opt.orElseThrow(() -> new ApplicationErrorException(
                                ApplicationError.of("warranty.claim.not-found", "Claim not found: " + command.claimId())));
                        claim.cancel(clock);
                        return saveAndPublish(claims.save(claim), claim)
                                .map(saved -> Result.success(saved.id().value().toString()));
                    });
        }
    }
}
