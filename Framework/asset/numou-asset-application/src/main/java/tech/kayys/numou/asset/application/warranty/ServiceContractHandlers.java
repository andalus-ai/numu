package tech.kayys.numou.asset.application.warranty;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.repository.ServiceContractRepository;
import tech.kayys.numou.asset.domain.warranty.ServiceContract;
import tech.kayys.numou.asset.domain.warranty.ServiceContractCoverage;
import tech.kayys.numou.asset.domain.warranty.ServiceContractId;
import tech.kayys.numou.asset.domain.warranty.ServiceContractType;
import tech.kayys.numou.asset.domain.warranty.ServiceEntitlement;
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
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Service contract registration + lifecycle + entitlement management (ASSET-23). */
public final class ServiceContractHandlers {
    private ServiceContractHandlers() {}

    public record RegisterServiceContractCommand(
            String tenantId, String assetId, String contractNumber, String providerId, String providerName,
            ServiceContractType type, Instant startsAt, Instant expiresAt) implements Command {}

    public static final class Register extends AbstractWarrantyCommandHandler
            implements CommandHandler<RegisterServiceContractCommand, Result<String>> {
        private final ServiceContractRepository contracts;

        public Register(ServiceContractRepository contracts, EventPublisher eventPublisher,
                        UnitOfWork unitOfWork, DomainClock clock) {
            super(eventPublisher, unitOfWork, clock);
            this.contracts = Objects.requireNonNull(contracts, "contracts");
        }

        @Override
        public Uni<Result<String>> handle(RegisterServiceContractCommand command) {
            return Uni.createFrom()
                    .completionStage(() -> contracts.existsByTenantAndContractNumber(
                            command.tenantId(), command.contractNumber()))
                    .flatMap(exists -> {
                        if (Boolean.TRUE.equals(exists)) {
                            return Uni.createFrom().item(Result.<String>failure(ApplicationError.of(
                                    "service-contract.number.duplicate",
                                    "Contract number already exists: " + command.contractNumber())));
                        }
                        ServiceContract contract = ServiceContract.create(ServiceContractId.generate(),
                                command.tenantId(), UUID.fromString(command.assetId()), command.contractNumber(),
                                command.providerId(), command.providerName(), command.type(),
                                command.startsAt(), command.expiresAt(), clock);
                        return saveAndPublish(contracts.save(contract), contract)
                                .map(saved -> Result.success(saved.id().value().toString()));
                    });
        }
    }

    public record ActivateServiceContractCommand(String tenantId, String contractId) implements Command {}
    public record SuspendServiceContractCommand(String tenantId, String contractId) implements Command {}
    public record TerminateServiceContractCommand(String tenantId, String contractId) implements Command {}

    abstract static class Lifecycle<C extends Command> extends AbstractWarrantyCommandHandler {
        protected final ServiceContractRepository contracts;
        protected final BiConsumer<ServiceContract, DomainClock> transition;

        Lifecycle(ServiceContractRepository contracts, EventPublisher eventPublisher,
                  UnitOfWork unitOfWork, DomainClock clock,
                  BiConsumer<ServiceContract, DomainClock> transition) {
            super(eventPublisher, unitOfWork, clock);
            this.contracts = Objects.requireNonNull(contracts, "contracts");
            this.transition = Objects.requireNonNull(transition, "transition");
        }

        protected Uni<Result<String>> apply(String tenantId, String contractId) {
            ServiceContractId id = ServiceContractId.of(UUID.fromString(contractId));
            return Uni.createFrom().completionStage(() -> contracts.findByTenantAndId(tenantId, id))
                    .flatMap(opt -> {
                        ServiceContract contract = opt.orElseThrow(() -> new ApplicationErrorException(
                                ApplicationError.of("service-contract.not-found", "Contract not found: " + contractId)));
                        transition.accept(contract, clock);
                        return saveAndPublish(contracts.save(contract), contract)
                                .map(saved -> Result.success(saved.id().value().toString()));
                    });
        }
    }

    public static final class Activate extends Lifecycle<ActivateServiceContractCommand>
            implements CommandHandler<ActivateServiceContractCommand, Result<String>> {
        public Activate(ServiceContractRepository contracts, EventPublisher eventPublisher,
                        UnitOfWork unitOfWork, DomainClock clock) {
            super(contracts, eventPublisher, unitOfWork, clock, ServiceContract::activate);
        }
        @Override public Uni<Result<String>> handle(ActivateServiceContractCommand command) {
            return apply(command.tenantId(), command.contractId());
        }
    }

    public static final class Suspend extends Lifecycle<SuspendServiceContractCommand>
            implements CommandHandler<SuspendServiceContractCommand, Result<String>> {
        public Suspend(ServiceContractRepository contracts, EventPublisher eventPublisher,
                       UnitOfWork unitOfWork, DomainClock clock) {
            super(contracts, eventPublisher, unitOfWork, clock, ServiceContract::suspend);
        }
        @Override public Uni<Result<String>> handle(SuspendServiceContractCommand command) {
            return apply(command.tenantId(), command.contractId());
        }
    }

    public static final class Terminate extends Lifecycle<TerminateServiceContractCommand>
            implements CommandHandler<TerminateServiceContractCommand, Result<String>> {
        public Terminate(ServiceContractRepository contracts, EventPublisher eventPublisher,
                         UnitOfWork unitOfWork, DomainClock clock) {
            super(contracts, eventPublisher, unitOfWork, clock, ServiceContract::terminate);
        }
        @Override public Uni<Result<String>> handle(TerminateServiceContractCommand command) {
            return apply(command.tenantId(), command.contractId());
        }
    }

    public record AddServiceEntitlementCommand(
            String tenantId, String contractId, String entitlementCode,
            BigDecimal allocatedQuantity) implements Command {}

    public static final class AddEntitlement extends AbstractWarrantyCommandHandler
            implements CommandHandler<AddServiceEntitlementCommand, Result<String>> {
        private final ServiceContractRepository contracts;

        public AddEntitlement(ServiceContractRepository contracts, EventPublisher eventPublisher,
                              UnitOfWork unitOfWork, DomainClock clock) {
            super(eventPublisher, unitOfWork, clock);
            this.contracts = Objects.requireNonNull(contracts, "contracts");
        }

        @Override
        public Uni<Result<String>> handle(AddServiceEntitlementCommand command) {
            ServiceContractId id = ServiceContractId.of(UUID.fromString(command.contractId()));
            return Uni.createFrom().completionStage(() -> contracts.findByTenantAndId(command.tenantId(), id))
                    .flatMap(opt -> {
                        ServiceContract contract = opt.orElseThrow(() -> new ApplicationErrorException(
                                ApplicationError.of("service-contract.not-found",
                                        "Contract not found: " + command.contractId())));
                        contract.addEntitlement(new ServiceEntitlement(UUID.randomUUID(),
                                command.entitlementCode(), command.allocatedQuantity(), BigDecimal.ZERO));
                        return saveAndPublish(contracts.save(contract), contract)
                                .map(saved -> Result.success(saved.id().value().toString()));
                    });
        }
    }

    public record ConsumeServiceEntitlementCommand(
            String tenantId, String contractId, String entitlementCode,
            BigDecimal quantity) implements Command {}

    public static final class ConsumeEntitlement extends AbstractWarrantyCommandHandler
            implements CommandHandler<ConsumeServiceEntitlementCommand, Result<String>> {
        private final ServiceContractRepository contracts;

        public ConsumeEntitlement(ServiceContractRepository contracts, EventPublisher eventPublisher,
                                  UnitOfWork unitOfWork, DomainClock clock) {
            super(eventPublisher, unitOfWork, clock);
            this.contracts = Objects.requireNonNull(contracts, "contracts");
        }

        @Override
        public Uni<Result<String>> handle(ConsumeServiceEntitlementCommand command) {
            ServiceContractId id = ServiceContractId.of(UUID.fromString(command.contractId()));
            return Uni.createFrom().completionStage(() -> contracts.findByTenantAndId(command.tenantId(), id))
                    .flatMap(opt -> {
                        ServiceContract contract = opt.orElseThrow(() -> new ApplicationErrorException(
                                ApplicationError.of("service-contract.not-found",
                                        "Contract not found: " + command.contractId())));
                        contract.consumeEntitlement(command.entitlementCode(), command.quantity());
                        return saveAndPublish(contracts.save(contract), contract)
                                .map(saved -> Result.success(saved.id().value().toString()));
                    });
        }
    }

    public record AddServiceContractCoverageCommand(
            String tenantId, String contractId, ServiceContractCoverage coverage) implements Command {}

    public static final class AddCoverage extends AbstractWarrantyCommandHandler
            implements CommandHandler<AddServiceContractCoverageCommand, Result<String>> {
        private final ServiceContractRepository contracts;

        public AddCoverage(ServiceContractRepository contracts, EventPublisher eventPublisher,
                           UnitOfWork unitOfWork, DomainClock clock) {
            super(eventPublisher, unitOfWork, clock);
            this.contracts = Objects.requireNonNull(contracts, "contracts");
        }

        @Override
        public Uni<Result<String>> handle(AddServiceContractCoverageCommand command) {
            ServiceContractId id = ServiceContractId.of(UUID.fromString(command.contractId()));
            return Uni.createFrom().completionStage(() -> contracts.findByTenantAndId(command.tenantId(), id))
                    .flatMap(opt -> {
                        ServiceContract contract = opt.orElseThrow(() -> new ApplicationErrorException(
                                ApplicationError.of("service-contract.not-found",
                                        "Contract not found: " + command.contractId())));
                        contract.addCoverage(command.coverage());
                        return saveAndPublish(contracts.save(contract), contract)
                                .map(saved -> Result.success(saved.id().value().toString()));
                    });
        }
    }
}
