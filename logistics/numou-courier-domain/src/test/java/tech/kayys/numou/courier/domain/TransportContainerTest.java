package tech.kayys.numou.courier.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransportContainerTest {
    private static final Instant T0 = Instant.parse("2026-10-24T00:00:00Z");

    @Test
    void containerRequiresParcelsAndSealBeforeDispatch() {
        TransportContainer container = container();
        assertThrows(IllegalStateException.class, () -> container.seal("SEAL-1", "operator-1", T0.plusSeconds(1)));
        assertThrows(IllegalStateException.class, () -> container.dispatch("MANIFEST-1", T0.plusSeconds(1)));

        container.addParcel("PARCEL-1", T0.plusSeconds(1));
        container.addParcel("PARCEL-2", T0.plusSeconds(2));
        assertThrows(IllegalArgumentException.class, () -> container.addParcel("PARCEL-1", T0.plusSeconds(3)));
        container.seal("SEAL-1", "operator-1", T0.plusSeconds(4));
        assertEquals(TransportContainerStatus.SEALED, container.status());
        assertThrows(IllegalStateException.class, () -> container.addParcel("PARCEL-3", T0.plusSeconds(5)));

        container.dispatch("MANIFEST-1", T0.plusSeconds(6));
        assertEquals(TransportContainerStatus.DISPATCHED, container.status());
        container.receive("operator-2", "SEAL-1", T0.plusSeconds(7));
        assertEquals(TransportContainerStatus.RECEIVED, container.status());
        assertEquals("operator-2", container.receivedBy());
    }

    @Test
    void sealMismatchIsRejectedAndDoesNotAdvanceCustody() {
        TransportContainer container = container();
        container.addParcel("PARCEL-1", T0.plusSeconds(1));
        container.seal("SEAL-1", "operator-1", T0.plusSeconds(2));
        container.dispatch("MANIFEST-1", T0.plusSeconds(3));

        assertThrows(IllegalStateException.class,
                () -> container.receive("operator-2", "SEAL-WRONG", T0.plusSeconds(4)));
        assertEquals(TransportContainerStatus.DISPATCHED, container.status());
        assertNull(container.receivedAt());
    }

    @Test
    void preventsBackwardsTimeAndCancellationAfterSeal() {
        TransportContainer container = container();
        container.addParcel("PARCEL-1", T0.plusSeconds(2));
        assertThrows(IllegalArgumentException.class, () -> container.addParcel("PARCEL-2", T0.plusSeconds(1)));
        assertEquals(1, container.parcelReferences().size());
        assertEquals(T0.plusSeconds(2), container.updatedAt());

        container.seal("SEAL-1", "operator-1", T0.plusSeconds(3));
        assertThrows(IllegalStateException.class, () -> container.cancel(T0.plusSeconds(4)));
        assertThrows(IllegalArgumentException.class, () -> container.dispatch("MANIFEST-1", T0.plusSeconds(2)));
        assertEquals(TransportContainerStatus.SEALED, container.status());
    }

    private static TransportContainer container() {
        return TransportContainer.create(UUID.randomUUID(), TenantId.generate(), "CONT-100",
                TransportContainerType.BAG, "HUB-BDG", "HUB-JKT", T0);
    }
}
