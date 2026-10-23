package tech.kayys.numou.courier.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.courier.application.port.OrganizationLookup;
import tech.kayys.numou.courier.application.port.OrganizationUnitLookup;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RegisterHubHandlerTest {
    private final TenantId tenant = TenantId.generate();
    private final TenantId otherTenant = TenantId.generate();
    private final OrganizationId organizationId = OrganizationId.generate();
    private final OrganizationUnitId branchId = OrganizationUnitId.generate();
    private final OrganizationUnit branch = OrganizationUnit.create(branchId, organizationId, null,
            OrganizationUnitKind.BRANCH, "Jakarta Branch", "JKT");
    private final Organization organization = mock(Organization.class);
    private final HubRepository hubs = mock(HubRepository.class);
    private final OrganizationLookup organizations = mock(OrganizationLookup.class);
    private final OrganizationUnitLookup units = mock(OrganizationUnitLookup.class);
    private final RegisterHubHandler handler = new RegisterHubHandler(
            hubs, organizations, units,
            Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneId.of("UTC")));

    private RegisterHubCommand command() {
        return new RegisterHubCommand(tenant, organizationId, branchId, "jkt-01", "Jakarta Hub",
                HubType.ORIGIN_HUB, new HubAddress("Street 1", null, "Jakarta", null, null, "ID",
                -6.2, 106.8), ZoneId.of("Asia/Jakarta"));
    }

    private void allowOrganization() {
        when(organizations.findById(tenant, organizationId))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(organization)));
        when(organization.getTenantId()).thenReturn(tenant);
        when(organization.isActive()).thenReturn(true);
    }

    @Test
    void registersHubAfterOrganizationBranchAndTenantChecks() {
        allowOrganization();
        when(units.findById(organizationId, branchId))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(branch)));
        when(hubs.existsByCode(tenant, "JKT-01")).thenReturn(CompletableFuture.completedFuture(false));
        when(hubs.save(eq(tenant), any(Hub.class))).thenAnswer(invocation ->
                CompletableFuture.completedFuture(invocation.getArgument(1)));

        Hub result = handler.handle(command(),
                new HubAccessScope(tenant, Set.of(branchId), Set.of(), false)).toCompletableFuture().join();

        assertThat(result.tenantId()).isEqualTo(tenant);
        assertThat(result.code()).isEqualTo("JKT-01");
        verify(hubs).save(eq(tenant), any(Hub.class));
    }

    @Test
    void rejectsUnauthorizedBranchBeforeAnyLookup() {
        assertThatThrownBy(() -> handler.handle(command(),
                new HubAccessScope(tenant, Set.of(), Set.of(), false)).toCompletableFuture().join())
                .hasCauseInstanceOf(SecurityException.class);
        verifyNoInteractions(organizations, units, hubs);
    }

    @Test
    void rejectsOrganizationOwnedByAnotherTenantBeforeBranchLookup() {
        when(organizations.findById(tenant, organizationId))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(organization)));
        when(organization.getTenantId()).thenReturn(otherTenant);

        assertThatThrownBy(() -> handler.handle(command(),
                new HubAccessScope(tenant, Set.of(branchId), Set.of(), false)).toCompletableFuture().join())
                .hasCauseInstanceOf(SecurityException.class);
        verifyNoInteractions(units, hubs);
    }

    @Test
    void rejectsInactiveOrganization() {
        when(organizations.findById(tenant, organizationId))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(organization)));
        when(organization.getTenantId()).thenReturn(tenant);
        when(organization.isActive()).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(command(),
                new HubAccessScope(tenant, Set.of(branchId), Set.of(), false)).toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalStateException.class);
        verifyNoInteractions(units, hubs);
    }

    @Test
    void rejectsDuplicateCodeWithinTenant() {
        allowOrganization();
        when(units.findById(organizationId, branchId))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(branch)));
        when(hubs.existsByCode(tenant, "JKT-01")).thenReturn(CompletableFuture.completedFuture(true));

        assertThatThrownBy(() -> handler.handle(command(),
                new HubAccessScope(tenant, Set.of(branchId), Set.of(), false)).toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalStateException.class);
        verify(hubs, never()).save(any(), any());
    }
}
