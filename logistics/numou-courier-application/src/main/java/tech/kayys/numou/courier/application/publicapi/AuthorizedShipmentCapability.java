package tech.kayys.numou.courier.application.publicapi;

import tech.kayys.numou.courier.application.port.CourierShipmentAccess;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Security-enforcing public facade for shipment operations.
 *
 * <p>Compose this facade at the application boundary instead of exposing the lower-level
 * {@link ShipmentCapability} directly to HTTP, messaging, or other untrusted adapters.
 * The actor context must come from a trusted authentication/composition boundary.</p>
 */
public final class AuthorizedShipmentCapability {
    private final ShipmentCapability shipments;
    private final CourierShipmentAccess access;

    public AuthorizedShipmentCapability(ShipmentCapability shipments, CourierShipmentAccess access) {
        this.shipments = Objects.requireNonNull(shipments, "shipments must not be null");
        this.access = Objects.requireNonNull(access, "access must not be null");
    }

    public CompletionStage<ShipmentCapability.ShipmentView> book(
            CourierActorContext actor, ShipmentCapability.BookRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        TenantId requestTenant = TenantId.of(request.tenantId());
        if (!requestTenant.equals(requireActor(actor).tenantId())) {
            return denied("booking tenant does not match authenticated tenant");
        }
        return authorize(actor, CourierShipmentAccess.Action.BOOK_SHIPMENT, request.reference())
                .thenCompose(ignored -> shipments.book(request));
    }

    public CompletionStage<Optional<ShipmentCapability.ShipmentView>> findById(
            CourierActorContext actor, String shipmentId) {
        requireText(shipmentId, "shipmentId");
        return authorize(actor, CourierShipmentAccess.Action.READ_SHIPMENT, shipmentId)
                .thenCompose(ignored -> shipments.findById(actor.tenantId().toString(), shipmentId));
    }

    public CompletionStage<Optional<ShipmentCapability.ShipmentView>> findByReference(
            CourierActorContext actor, String reference) {
        requireText(reference, "reference");
        return authorize(actor, CourierShipmentAccess.Action.READ_SHIPMENT, reference)
                .thenCompose(ignored -> shipments.findByReference(actor.tenantId().toString(), reference));
    }

    public CompletionStage<ShipmentCapability.TrackingPage> trackingTimeline(
            CourierActorContext actor, String shipmentId,
            ShipmentCapability.TrackingPosition before, int limit) {
        requireText(shipmentId, "shipmentId");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return authorize(actor, CourierShipmentAccess.Action.READ_TRACKING, shipmentId)
                .thenCompose(ignored -> shipments.trackingTimeline(
                        actor.tenantId().toString(), shipmentId, before, limit));
    }

    private CompletionStage<Void> authorize(CourierActorContext actor,
                                             CourierShipmentAccess.Action action,
                                             String resourceReference) {
        CourierActorContext trusted = requireActor(actor);
        CompletionStage<CourierShipmentAccess.Decision> decisionStage = Objects.requireNonNull(
                access.authorize(new CourierShipmentAccess.Request(trusted.tenantId(), trusted.actorReference(),
                        trusted.correlationId(), action, resourceReference)),
                "authorization port returned null CompletionStage");
        return decisionStage.thenCompose(decision -> {
            if (decision == null || !decision.allowed()) {
                return denied("courier operation is not authorized");
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    private static CourierActorContext requireActor(CourierActorContext actor) {
        return Objects.requireNonNull(actor, "actor context must not be null");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static <T> CompletionStage<T> denied(String message) {
        return CompletableFuture.failedFuture(new SecurityException(message));
    }
}
