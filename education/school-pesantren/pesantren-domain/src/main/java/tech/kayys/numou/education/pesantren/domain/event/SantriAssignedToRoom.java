package tech.kayys.numou.education.pesantren.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.pesantren.domain.DormRoomId;
import tech.kayys.numou.education.pesantren.domain.DormitoryId;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record SantriAssignedToRoom(
        UUID eventId,
        Instant occurredAt,
        DormitoryId dormitoryId,
        TenantId tenantId,
        SantriId santriId,
        DormRoomId roomId
) implements DomainEvent {
    public SantriAssignedToRoom(DormitoryId dormitoryId, TenantId tenantId, SantriId santriId, DormRoomId roomId) {
        this(UUID.randomUUID(), Instant.now(), dormitoryId, tenantId, santriId, roomId);
    }

    @Override
    public String eventType() {
        return "pesantren.dormitory.santri-assigned";
    }
}
