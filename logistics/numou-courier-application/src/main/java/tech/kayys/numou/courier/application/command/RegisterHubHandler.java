package tech.kayys.numou.courier.application.command;

import tech.kayys.numou.courier.application.port.OrganizationLookup;
import tech.kayys.numou.courier.application.port.OrganizationUnitLookup;
import tech.kayys.numou.courier.domain.Hub;
import tech.kayys.numou.courier.domain.HubAccessScope;
import tech.kayys.numou.courier.domain.HubId;
import tech.kayys.numou.courier.domain.HubRegistrationPolicy;
import tech.kayys.numou.courier.domain.HubRepository;
import tech.kayys.numou.organization.domain.Organization;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Registers a hub only after authorization, tenant ownership validation,
 * branch validation, and tenant-scoped duplicate-code checks. Database
 * uniqueness remains the final concurrency guard.
 */
public final class RegisterHubHandler {
    private final HubRepository hubs;
    private final OrganizationLookup organizations;
    private final OrganizationUnitLookup organizationUnits;
    private final Clock clock;

    public RegisterHubHandler(HubRepository hubs, OrganizationLookup organizations,
                              OrganizationUnitLookup organizationUnits, Clock clock) {
        this.hubs = Objects.requireNonNull(hubs, "hubs must not be null");
        this.organizations = Objects.requireNonNull(organizations, "organizations must not be null");
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

        return organizations.findById(command.organizationId()).thenCompose(foundOrganization -> {
            if (foundOrganization.isEmpty()) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("organization was not found for this tenant"));
            }
            Organization organization = foundOrganization.get();
            if (!command.tenantId().equals(organization.getTenantId())) {
                return CompletableFuture.failedFuture(
                        new SecurityException("organization does not belong to the requested tenant"));
            }
            if (!organization.isActive()) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("hubs can only be registered under an active organization"));
            }

            return organizationUnits.findById(command.organizationId(), command.branchUnitId())
                    .thenCompose(foundBranch -> {
                        if (foundBranch.isEmpty()) {
                            return CompletableFuture.failedFuture(
                                    new IllegalArgumentException("branch was not found in the requested organization"));
                        }
                        try {
                            HubRegistrationPolicy.requireEligibleBranch(command.organizationId(), foundBranch.get());
                        } catch (RuntimeException invalidBranch) {
                            return CompletableFuture.failedFuture(invalidBranch);
                        }

                        String normalizedCode = command.code().trim().toUpperCase(Locale.ROOT);
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
        });
    }
}
