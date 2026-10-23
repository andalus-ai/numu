package tech.kayys.numou.courier.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class HubTransferTest {
    @Test
    void manifestMustContainShipmentsBeforeSealAndFollowCustodyLifecycle() {
        Instant now = Instant.parse("2026-10-24T00:00:00Z");
        HubTransfer transfer = HubTransfer.draft("TRF-001", TenantId.generate(), HubId.generate(),
                HubId.generate(), HubId.generate(), now);
        assertThrows(IllegalStateException.class, () -> transfer.seal(now));
        transfer.addShipment(ShipmentId.generate(), now.plusSeconds(1));
        transfer.seal(now.plusSeconds(2));
        assertEquals(HubTransferStatus.SEALED, transfer.status());
        assertThrows(IllegalStateException.class, () -> transfer.addShipment(ShipmentId.generate(), now.plusSeconds(3)));
        transfer.depart(now.plusSeconds(3));
        transfer.arrive(now.plusSeconds(4));
        assertEquals(HubTransferStatus.ARRIVED, transfer.status());
        assertEquals(now.plusSeconds(3), transfer.departedAt());
        assertEquals(now.plusSeconds(4), transfer.arrivedAt());
    }

    @Test
    void preventsDuplicateShipmentAndSameHubTransfer() {
        Instant now = Instant.parse("2026-10-24T00:00:00Z");
        HubId hub = HubId.generate();
        assertThrows(IllegalArgumentException.class, () -> HubTransfer.draft("TRF-002", TenantId.generate(),
                HubId.generate(), hub, hub, now));
        HubTransfer transfer = HubTransfer.draft("TRF-003", TenantId.generate(), HubId.generate(),
                HubId.generate(), HubId.generate(), now);
        ShipmentId shipment = ShipmentId.generate();
        transfer.addShipment(shipment, now);
        assertThrows(IllegalArgumentException.class, () -> transfer.addShipment(shipment, now));
    }
}
