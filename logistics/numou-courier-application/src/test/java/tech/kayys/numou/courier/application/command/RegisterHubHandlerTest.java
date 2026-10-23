package tech.kayys.numou.courier.application.command;

import org.junit.jupiter.api.Test;
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
    private final OrganizationId organization = OrganizationId.generate();
    private final OrganizationUnitId branchId = OrganizationUnitId.generate();
    private final OrganizationUnit branch = OrganizationUnit.create(branchId, organization, null,
            OrganizationUnitKind.BRANCH, "Jakarta Branch", "JKT");
    private final HubRepository hubs = mock(HubRepository.class);
    private final OrganizationUnitLookup units = mock(OrganizationUnitLookup.class);
    private final RegisterHubHandler handler = new RegisterHubHandler(
            hubs, units, Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneId.of("UTC")));

    private RegisterHubCommand command() {
        return new RegisterHubCommand(tenant, organization, branchId, "jkt-01", "Jakarta Hub",
                HubType.ORIGIN_HUB, new HubAddress("Street 1", null, "Jakarta", null, null, "ID",
                -6.2, 106.8), ZoneId.of("Asia/Jakarta"));
    }

    @Test
    void registersHubAfterBranchAndTenantChecks() {
        when(units.findById(organization, branchId)).thenReturn(CompletableFuture.completedFuture(Optional.of(branch)));
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
    void rejectsUnauthorizedBranchBeforeLookingItUp() {
        assertThatThrownBy(() -> handler.handle(command(),
                new HubAccessScope(tenant, Set.of(), Set.of(), false)).toCompletableFuture().join())
                .hasCauseInstanceOf(SecurityException.class);
        verifyNoInteractions(units, hubs);
    }

    @Test
    void rejectsDuplicateCodeWithinTenant() {
        when(units.findById(organization, branchId)).thenReturn(CompletableFuture.completedFuture(Optional.of(branch)));
        when(hubs.existsByCode(tenant, "JKT-01")).thenReturn(CompletableFuture.completedFuture(true));

        assertThatThrownBy(() -> handler.handle(command(),
                new HubAccessScope(tenant, Set.of(branchId), Set.of(), false)).toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalStateException.class);
        verify(hubs, never()).save(any(), any());
    }
}
