package tech.kayys.numou.courier.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.courier.application.port.DispatchWaveDiscrepancyResolutionAuthorizer;
import tech.kayys.numou.courier.application.port.DispatchWaveDiscrepancyResolutionStore;
import tech.kayys.numou.courier.application.port.DispatchWaveDiscrepancyResolutionStore.Command;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResolveDispatchWaveDiscrepancyTest {
    private final DispatchWaveDiscrepancyResolutionAuthorizer authorizer =
            mock(DispatchWaveDiscrepancyResolutionAuthorizer.class);
    private final DispatchWaveDiscrepancyResolutionStore store =
            mock(DispatchWaveDiscrepancyResolutionStore.class);
    private final ResolveDispatchWaveDiscrepancy useCase =
            new ResolveDispatchWaveDiscrepancy(authorizer, store);
    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID waveId = UUID.randomUUID();
    private final UUID discrepancyId = UUID.randomUUID();

    @Test
    void rejectsUnauthorizedResolutionWithoutWritingEvidence() {
        when(authorizer.authorize(any())).thenReturn(CompletableFuture.completedFuture(
                new DispatchWaveDiscrepancyResolutionAuthorizer.Decision(false, null)));

        assertThrows(java.util.concurrent.CompletionException.class,
                () -> useCase.execute(request()).toCompletableFuture().join());
        verifyNoInteractions(store);
    }

    @Test
    void persistsOnlyAfterTrustedAuthorizationAndUsesDecisionReference() {
        when(authorizer.authorize(any())).thenReturn(CompletableFuture.completedFuture(
                new DispatchWaveDiscrepancyResolutionAuthorizer.Decision(true, "policy-decision-884")));
        when(store.resolve(any())).thenReturn(CompletableFuture.completedFuture(
                new DispatchWaveDiscrepancyResolutionStore.Result(
                        DispatchWaveDiscrepancyResolutionStore.Outcome.ACCEPTED, "resolution-1")));

        DispatchWaveDiscrepancyResolutionStore.Result result =
                useCase.execute(request()).toCompletableFuture().join();

        assertEquals(DispatchWaveDiscrepancyResolutionStore.Outcome.ACCEPTED, result.outcome());
        var captor = org.mockito.ArgumentCaptor.forClass(Command.class);
        verify(store).resolve(captor.capture());
        assertEquals("policy-decision-884", captor.getValue().permissionReference());
        assertEquals("operator-7", captor.getValue().actorReference());
        assertEquals("ACKNOWLEDGE_DUPLICATE", captor.getValue().resolutionCode());
    }

    @Test
    void rejectsResolutionCodeThatDoesNotFitMissingParcelShape() {
        ResolveDispatchWaveDiscrepancy.Request invalid = new ResolveDispatchWaveDiscrepancy.Request(
                tenant, UUID.randomUUID(), waveId, null, "parcel-9", "ACCEPT_UNEXPECTED",
                "reason", "operator-7", null, "scanner", "key-1", "fingerprint", Instant.now());

        var failure = assertThrows(java.util.concurrent.CompletionException.class,
                () -> useCase.execute(invalid).toCompletableFuture().join());
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        verifyNoInteractions(authorizer, store);
    }

    private ResolveDispatchWaveDiscrepancy.Request request() {
        return new ResolveDispatchWaveDiscrepancy.Request(tenant, UUID.randomUUID(), waveId, discrepancyId,
                "parcel-9", "ACKNOWLEDGE_DUPLICATE", "duplicate scan confirmed", "operator-7",
                null, "scanner", "key-1", "fingerprint-1", Instant.parse("2026-10-24T10:00:00Z"));
    }
}