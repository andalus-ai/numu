package tech.kayys.numou.hospitality.spi;

import io.smallrye.mutiny.Uni;
import java.util.Optional;
import java.util.UUID;
import tech.kayys.numou.hospitality.domain.StayBooking;

/** Tenant-scoped booking persistence; idempotency keys are unique within a tenant/customer scope. */
public interface StayBookingRepository {
    Uni<Optional<StayBooking>> findByIdempotencyKey(String tenantId, String customerId, String idempotencyKey);
    Uni<Optional<StayBooking>> findById(String tenantId, UUID bookingId);
}
