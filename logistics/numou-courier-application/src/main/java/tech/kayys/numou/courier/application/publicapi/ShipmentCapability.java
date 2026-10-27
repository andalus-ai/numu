package tech.kayys.numou.courier.application.publicapi;

import tech.kayys.numou.courier.application.command.BookShipmentCommand;
import tech.kayys.numou.courier.application.command.BookShipmentHandler;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Framework-neutral application contract for consumers that need shipment booking,
 * tenant-scoped lookup, and tracking reads without depending on courier persistence adapters.
 *
 * Tenant identifiers must be supplied by a trusted composition/authentication boundary;
 * this class is not an authorization mechanism.
 */
public final class ShipmentCapability {
    private final BookShipmentHandler booking;
    private final ShipmentRepository shipments;
    private final TrackingEventRepository tracking;

    public ShipmentCapability(BookShipmentHandler booking,
                              ShipmentRepository shipments,
                              TrackingEventRepository tracking) {
        this.booking = Objects.requireNonNull(booking, "booking must not be null");
        this.shipments = Objects.requireNonNull(shipments, "shipments must not be null");
        this.tracking = Objects.requireNonNull(tracking, "tracking must not be null");
    }

    public CompletionStage<ShipmentView> book(BookRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        TenantId tenant = TenantId.of(request.tenantId());
        ShipmentId shipmentId = request.shipmentId() == null || request.shipmentId().isBlank()
                ? ShipmentId.generate() : ShipmentId.of(request.shipmentId());
        List<Parcel> parcels = request.parcels().stream().map(p -> new Parcel(
                p.parcelId() == null || p.parcelId().isBlank() ? ParcelId.generate() : ParcelId.of(p.parcelId()),
                p.reference(), p.weightKg(), p.lengthCm(), p.widthCm(), p.heightCm(), p.handlingInstructions()
        )).toList();
        BookShipmentCommand command = new BookShipmentCommand(
                tenant, shipmentId, request.reference(), request.sender().toDomain(),
                request.recipient().toDomain(), ServiceLevel.valueOf(request.serviceLevel().trim().toUpperCase()),
                parcels, request.occurredAt());
        return booking.handle(command).thenApply(ShipmentCapability::view);
    }

    public CompletionStage<Optional<ShipmentView>> findById(String tenantId, String shipmentId) {
        TenantId tenant = TenantId.of(tenantId);
        ShipmentId id = ShipmentId.of(shipmentId);
        return shipments.findById(tenant, id).thenApply(found -> found.map(ShipmentCapability::view));
    }

    public CompletionStage<Optional<ShipmentView>> findByReference(String tenantId, String reference) {
        TenantId tenant = TenantId.of(tenantId);
        if (reference == null || reference.isBlank()) throw new IllegalArgumentException("reference must not be blank");
        return shipments.findByReference(tenant, reference).thenApply(found -> found.map(ShipmentCapability::view));
    }

    public CompletionStage<TrackingPage> trackingTimeline(String tenantId, String shipmentId,
                                                           TrackingPosition before, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        TenantId tenant = TenantId.of(tenantId);
        ShipmentId id = ShipmentId.of(shipmentId);
        TrackingCursor cursor = before == null ? null
                : new TrackingCursor(before.occurredAt(), TrackingEventId.of(before.eventId()));
        return tracking.findByShipment(tenant, id, cursor, limit).thenApply(events -> {
            List<TrackingView> items = events.stream().map(ShipmentCapability::view).toList();
            TrackingPosition next = items.isEmpty() ? null
                    : new TrackingPosition(items.get(items.size() - 1).occurredAt(),
                    items.get(items.size() - 1).eventId());
            return new TrackingPage(items, next);
        });
    }

    private static ShipmentView view(Shipment shipment) {
        return new ShipmentView(shipment.id().toString(), shipment.tenantId().toString(),
                shipment.reference(), shipment.status().name(), shipment.serviceLevel().name(),
                shipment.createdAt(), shipment.updatedAt(), shipment.bookedAt(), shipment.deliveredAt(),
                address(shipment.sender()), address(shipment.recipient()),
                shipment.parcels().stream().map(p -> new ParcelView(p.id().toString(), p.reference(),
                        p.weightKg(), p.lengthCm(), p.widthCm(), p.heightCm(), p.handlingInstructions())).toList());
    }

    private static Address address(ShipmentAddress a) {
        return new Address(a.contactName(), a.phone(), a.addressLine1(), a.addressLine2(),
                a.locality(), a.administrativeArea(), a.postalCode(), a.countryCode());
    }

    private static TrackingView view(TrackingEvent event) {
        return new TrackingView(event.id().toString(), event.shipmentId().toString(),
                event.parcelId() == null ? null : event.parcelId().toString(), event.type().name(),
                event.occurredAt(), event.recordedAt(), event.locationCode(), event.actorReference(),
                event.source(), event.idempotencyKey(), event.note());
    }

    public record BookRequest(String tenantId, String shipmentId, String reference,
                              Address sender, Address recipient, String serviceLevel,
                              List<ParcelInput> parcels, Instant occurredAt) {
        public BookRequest {
            require(tenantId, "tenantId");
            require(reference, "reference");
            Objects.requireNonNull(sender, "sender must not be null");
            Objects.requireNonNull(recipient, "recipient must not be null");
            require(serviceLevel, "serviceLevel");
            parcels = List.copyOf(Objects.requireNonNull(parcels, "parcels must not be null"));
            if (parcels.isEmpty()) throw new IllegalArgumentException("at least one parcel is required");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }

    public record Address(String contactName, String phone, String addressLine1, String addressLine2,
                          String locality, String administrativeArea, String postalCode, String countryCode) {
        public Address {
            require(contactName, "contactName");
            require(phone, "phone");
            require(addressLine1, "addressLine1");
            require(locality, "locality");
            require(countryCode, "countryCode");
        }
        private ShipmentAddress toDomain() {
            return new ShipmentAddress(contactName, phone, addressLine1, addressLine2, locality,
                    administrativeArea, postalCode, countryCode);
        }
    }

    public record ParcelInput(String parcelId, String reference, BigDecimal weightKg,
                              BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm,
                              String handlingInstructions) {
        public ParcelInput {
            require(reference, "parcel reference");
            Objects.requireNonNull(weightKg, "weightKg must not be null");
            Objects.requireNonNull(lengthCm, "lengthCm must not be null");
            Objects.requireNonNull(widthCm, "widthCm must not be null");
            Objects.requireNonNull(heightCm, "heightCm must not be null");
        }
    }

    public record ParcelView(String id, String reference, BigDecimal weightKg, BigDecimal lengthCm,
                             BigDecimal widthCm, BigDecimal heightCm, String handlingInstructions) {}
    public record ShipmentView(String id, String tenantId, String reference, String status,
                               String serviceLevel, Instant createdAt, Instant updatedAt,
                               Instant bookedAt, Instant deliveredAt, Address sender, Address recipient,
                               List<ParcelView> parcels) {
        public ShipmentView { parcels = List.copyOf(parcels); }
    }
    public record TrackingPosition(Instant occurredAt, String eventId) {
        public TrackingPosition {
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            require(eventId, "eventId");
            TrackingEventId.of(eventId);
        }
    }
    public record TrackingView(String eventId, String shipmentId, String parcelId, String type,
                               Instant occurredAt, Instant recordedAt, String locationCode,
                               String actorReference, String source, String idempotencyKey, String note) {}
    public record TrackingPage(List<TrackingView> events, TrackingPosition nextBefore) {
        public TrackingPage { events = List.copyOf(events); }
    }

    private static String require(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
