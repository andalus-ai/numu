package tech.kayys.numou.courier.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ShipmentTest {
    private static final Instant T0 = Instant.parse("2026-10-24T00:00:00Z");
    private static final ShipmentAddress SENDER = new ShipmentAddress(
            "Sender", "+621111", "Street 1", null, "Bandung", "West Java", "40111", "ID");
    private static final ShipmentAddress RECIPIENT = new ShipmentAddress(
            "Recipient", "+622222", "Street 2", "Unit 3", "Jakarta", "DKI Jakarta", "10110", "ID");

    @Test
    void bookingRequiresAtLeastOneParcelAndLocksDraftMutation() {
        Shipment shipment = draft("NUM-1001");
        assertThrows(IllegalStateException.class, () -> shipment.book(T0.plusSeconds(1)));
        shipment.addParcel(parcel("PIECE-1"), T0.plusSeconds(1));
        shipment.book(T0.plusSeconds(2));

        assertEquals(ShipmentStatus.BOOKED, shipment.status());
        assertEquals(T0.plusSeconds(2), shipment.bookedAt());
        assertThrows(IllegalStateException.class,
                () -> shipment.addParcel(parcel("PIECE-2"), T0.plusSeconds(3)));
    }

    @Test
    void parcelIdentityAndReferenceMustBeUniqueWithinShipment() {
        Shipment shipment = draft("NUM-1002");
        Parcel parcel = parcel("PIECE-1");
        shipment.addParcel(parcel, T0.plusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> shipment.addParcel(parcel, T0.plusSeconds(2)));
        assertThrows(IllegalArgumentException.class,
                () -> shipment.addParcel(parcel("piece-1"), T0.plusSeconds(2)));
    }

    @Test
    void rejectsInvalidParcelDimensionsAndBackwardTime() {
        assertThrows(IllegalArgumentException.class, () -> new Parcel(
                ParcelId.generate(), "PIECE-3", BigDecimal.ZERO,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, null));
        Shipment shipment = draft("NUM-1003");
        assertThrows(IllegalArgumentException.class,
                () -> shipment.addParcel(parcel("PIECE-1"), T0.minusSeconds(1)));
    }

    @Test
    void volumetricWeightUsesConfiguredDivisorAndThreeDecimalPlaces() {
        Parcel parcel = new Parcel(ParcelId.generate(), "PIECE-4",
                new BigDecimal("2.5"), new BigDecimal("40"), new BigDecimal("30"),
                new BigDecimal("20"), null);
        assertEquals(new BigDecimal("4.000"), parcel.volumetricWeightKg(new BigDecimal("6000")));
    }

    @Test
    void cancellationIsIdempotentAndBookingCannotBeCancelledBackwardsInTime() {
        Shipment shipment = draft("NUM-1004");
        shipment.addParcel(parcel("PIECE-1"), T0.plusSeconds(1));
        shipment.book(T0.plusSeconds(2));
        assertThrows(IllegalArgumentException.class, () -> shipment.cancel(T0.plusSeconds(1)));
        shipment.cancel(T0.plusSeconds(3));
        shipment.cancel(T0.plusSeconds(2));
        assertEquals(ShipmentStatus.CANCELLED, shipment.status());
    }

    private static Shipment draft(String reference) {
        return Shipment.draft(ShipmentId.generate(), TenantId.generate(), reference,
                SENDER, RECIPIENT, ServiceLevel.STANDARD, T0);
    }

    private static Parcel parcel(String reference) {
        return new Parcel(ParcelId.generate(), reference, new BigDecimal("1.250"),
                new BigDecimal("20"), new BigDecimal("15"), new BigDecimal("10"), "Keep dry");
    }
}