package tech.kayys.numou.hospitality.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.smallrye.mutiny.Uni;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.hospitality.domain.StayBooking;
import tech.kayys.numou.hospitality.spi.StayBookingLifecyclePort;

class ExpireStayBookingHoldsServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-26T03:00:00Z");

    @Test
    void passesClockInstantAndConfiguredBoundToLifecyclePort() {
        RecordingLifecyclePort port = new RecordingLifecyclePort();
        ExpireStayBookingHoldsService service = new ExpireStayBookingHoldsService(
                port, Clock.fixed(NOW, ZoneOffset.UTC), 75);

        assertThat(service.runOnce().await().indefinitely()).isEqualTo(4);
        assertThat(port.now).isEqualTo(NOW);
        assertThat(port.batchSize).isEqualTo(75);
    }

    @Test
    void rejectsUnboundedOrInvalidBatchSizes() {
        RecordingLifecyclePort port = new RecordingLifecyclePort();
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ExpireStayBookingHoldsService(port, Clock.systemUTC(), 0));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ExpireStayBookingHoldsService(port, Clock.systemUTC(), 1001));
    }

    private static final class RecordingLifecyclePort implements StayBookingLifecyclePort {
        private Instant now;
        private int batchSize;

        @Override
        public Uni<Optional<StayBooking>> confirm(String tenantId, UUID bookingId, Instant at) {
            return Uni.createFrom().failure(new UnsupportedOperationException());
        }

        @Override
        public Uni<Optional<StayBooking>> cancel(String tenantId, UUID bookingId, Instant at) {
            return Uni.createFrom().failure(new UnsupportedOperationException());
        }

        @Override
        public Uni<Integer> expireDue(Instant at, int limit) {
            now = at;
            batchSize = limit;
            return Uni.createFrom().item(4);
        }
    }
}
