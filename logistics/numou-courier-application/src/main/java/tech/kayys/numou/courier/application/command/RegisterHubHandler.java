package tech.kayys.numou.courier.application.command;

import tech.kayys.numou.courier.application.port.OrganizationUnitLookup;
import tech.kayys.numou.courier.domain.Hub;
import tech.kayys.numou.courier.domain.HubAccessScope;
import tech.kayys.numou.courier.domain.HubId;
import tech.kayys.numou.courier.domain.HubRegistrationPolicy;
import tech.kayys.numou.courier.domain.HubRepository;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Registers a hub only after authorization, branch validation, and tenant-scoped
 * duplicate-code checks. Database uniqueness remains the final concurrency guard.
 */
public final class RegisterHubHandler {
    private final HubRepository hubs;
    private final OrganizationUnitLookup organizationUnits;
    private final Clock clock;

    public RegisterHubHandler(HubRepository hubs, OrganizationUnitLookup organizationUnits, Clock clock) {
        this.hubs = Objects.requireNonNull(hubs, "hubs must not be null");
        this.organizationUnits = Objects.requireNonNull(organizationUnits, "organizationUnits must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public CompletionStage<Hub> handle(RegisterHubCommand command, HubAccessScope accessScope) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(accessScope, "accessScope must not be null");

        try {
            accessScope.requireCanCreateInBranch(command.tenantId(), command.branchUnitId());
        } catch (RuntimeException denied) {
            return CompletableFuture.failedFuture(denied);
        }

        return organizationUnits.findById(command.organizationId(), command.branchUnitId())
                .thenCompose(found -> {
                    if (found.isEmpty()) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("branch was not found in the requested organization"));
                    }
                    try {
                        HubRegistrationPolicy.requireEligibleBranch(command.organizationId(), found.get());
                    } catch (RuntimeException invalidBranch) {
                        return CompletableFuture.failedFuture(invalidBranch);
                    }

                    String normalizedCode = command.code().trim().toUpperCase(java.util.Locale.ROOT);
                    return hubs.existsByCode(command.tenantId(), normalizedCode).thenCompose(exists -> {
                        if (exists) {
                            return CompletableFuture.failedFuture(
                                    new IllegalStateException("hub code already exists for this tenant"));
                        }
                        Hub hub = Hub.create(HubId.generate(), command.tenantId(), command.organizationId(),
                                command.branchUnitId(), normalizedCode, command.name(), command.type(),
                                command.address(), command.timeZone(), clock.instant());
                        return hubs.save(command.tenantId(), hub);
                    });
                });
    }
}
