package tech.kayys.numou.reliability.spi.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.reliability.outbox.OutboxClaim;

/** Drains leased outbox claims to the message transport. */
public interface OutboxRelayPort {
    Uni<Void> relay(OutboxClaim claim);
    Uni<Integer> runOnce();
}
