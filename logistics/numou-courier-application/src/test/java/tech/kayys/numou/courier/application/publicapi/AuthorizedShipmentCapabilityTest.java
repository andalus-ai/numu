package tech.kayys.numou.courier.application.publicapi;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.courier.application.command.BookShipmentHandler;
import tech.kayys.numou.courier.application.port.CourierShipmentAccess;
import tech.kayys.numou.courier.domain.Shipment;
import tech.kayys.numou.courier.domain.ShipmentRepository;
import tech.kayys.numou.courier.domain.TrackingEventRepository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthorizedShipmentCapabilityTest {
    private static final Instant NOW = Instant.parse("2026-11-02T08:00:00Z");

    @Test
    void authorizesBookingBeforePersistingAndUsesTrustedTenantContext() {
        TenantId tenant = TenantId.generate();
        ShipmentRepository repository = mock(ShipmentRepository.class);
        CourierShipmentAccess access = mock(CourierShipmentAccess.class);
        when(access.authorize(any())).thenReturn(CompletableFuture.completedFuture(
                new CourierShipmentAccess.Decision(true, "policy-1")));
        when(repository.save(eq(tenant), any(Shipment.class)))
                .thenAnswer(call -> CompletableFuture.completedFuture(call.getArgument(1)));
        AuthorizedShipmentCapability capability = capability(repository, access);
        var request = request(tenant.toString());

        var result = capability.book(actor(tenant), request).toCompletableFuture().join();

        assertEquals(tenant.toString(), result.tenantId());
        verify(access).authorize(argThat(r -> r.tenantId().equals(tenant)
                && r.actorReference().equals("user:operator-7")
                && r.correlationId().equals("corr-123")
                && r.action() == CourierShipmentAccess.Action.BOOK_SHIPMENT
                && r.resourceReference().equals("ORD-54")));
        verify(repository).save(eq(tenant), any(Shipment.class));
    }

    @Test
    void deniesBookingWithoutCallingShipmentRepository() {
        TenantId tenant = TenantId.generate();
        ShipmentRepository repository = mock(ShipmentRepository.class);
        CourierShipmentAccess access = mock(CourierShipmentAccess.class);
        when(access.authorize(any())).thenReturn(CompletableFuture.completedFuture(
                new CourierShipmentAccess.Decision(false, "policy-denied")));
        AuthorizedShipmentCapability capability = capability(repository, access);

        assertDenied(() -> capability.book(actor(tenant), request(tenant.toString())).toCompletableFuture().join());
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsTenantMismatchBeforeAuthorizationOrRepositoryAccess() {
        TenantId authenticatedTenant = TenantId.generate();
        TenantId suppliedTenant = TenantId.generate();
        ShipmentRepository repository = mock(ShipmentRepository.class);
        CourierShipmentAccess access = mock(CourierShipmentAccess.class);
        AuthorizedShipmentCapability capability = capability(repository, access);

        assertDenied(() -> capability.book(actor(authenticatedTenant),
                request(suppliedTenant.toString())).toCompletableFuture().join());
        verifyNoInteractions(access, repository);
    }

    @Test
    void authorizesReadsAndUsesTenantFromTrustedContext() {
        TenantId tenant = TenantId.generate();
        ShipmentRepository repository = mock(ShipmentRepository.class);
        CourierShipmentAccess access = mock(CourierShipmentAccess.class);
        when(access.authorize(any())).thenReturn(CompletableFuture.completedFuture(
                new CourierShipmentAccess.Decision(true, "policy-read")));
        when(repository.findByReference(tenant, "ORD-54"))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        AuthorizedShipmentCapability capability = capability(repository, access);

        assertTrue(capability.findByReference(actor(tenant), "ORD-54").toCompletableFuture().join().isEmpty());
        verify(access).authorize(argThat(r -> r.tenantId().equals(tenant)
                && r.action() == CourierShipmentAccess.Action.READ_SHIPMENT
                && r.resourceReference().equals("ORD-54")));
        verify(repository).findByReference(tenant, "ORD-54");
    }

    @Test
    void authorizationFailureFailsClosedAndDoesNotReadShipment() {
        TenantId tenant = TenantId.generate();
        ShipmentRepository repository = mock(ShipmentRepository.class);
        CourierShipmentAccess access = mock(CourierShipmentAccess.class);
        when(access.authorize(any())).thenReturn(CompletableFuture.failedFuture(
                new IllegalStateException("policy service unavailable")));
        AuthorizedShipmentCapability capability = capability(repository, access);

        CompletionException failure = assertThrows(CompletionException.class, () -> capability.findById(actor(tenant), "shipment-1")
                .toCompletableFuture().join());
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        verifyNoInteractions(repository);
    }

    private static void assertDenied(org.junit.jupiter.api.function.Executable action) {
        CompletionException failure = assertThrows(CompletionException.class, action);
        assertInstanceOf(SecurityException.class, failure.getCause());
    }

    private static AuthorizedShipmentCapability capability(ShipmentRepository repository,
                                                             CourierShipmentAccess access) {
        ShipmentCapability raw = new ShipmentCapability(new BookShipmentHandler(repository), repository,
                mock(TrackingEventRepository.class));
        return new AuthorizedShipmentCapability(raw, access);
    }

    private static CourierActorContext actor(TenantId tenant) {
        return new CourierActorContext(tenant, "user:operator-7", "corr-123");
    }

    private static ShipmentCapability.BookRequest request(String tenantId) {
        return new ShipmentCapability.BookRequest(tenantId, null, "ORD-54",
                new ShipmentCapability.Address("Sender", "+628111111111", "Street 1", null,
                        "Bandung", "West Java", "40111", "ID"),
                new ShipmentCapability.Address("Recipient", "+628222222222", "Street 2", null,
                        "Jakarta", "DKI Jakarta", "10110", "ID"),
                "STANDARD",
                List.of(new ShipmentCapability.ParcelInput(null, "BOX-54", new BigDecimal("1.0"),
                        new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10"), null)),
                NOW);
    }
}
