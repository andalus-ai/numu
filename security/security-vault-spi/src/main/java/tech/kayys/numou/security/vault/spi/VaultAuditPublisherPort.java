package tech.kayys.numou.security.vault.spi;

import io.smallrye.mutiny.Uni;

/** Publishes an audit event to the host's configured event transport. */
public interface VaultAuditPublisherPort {
    Uni<Void> publish(VaultAuditOutboxEvent event);
}
