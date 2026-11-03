package tech.kayys.numou.security.vault.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.security.vault.domain.VaultPolicySnapshot;
import tech.kayys.numou.security.vault.domain.VaultPolicyChangeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped immutable policy snapshot storage and optimistic publication contract. */
public interface VaultPolicyRepositoryPort {
    Uni<Optional<VaultPolicySnapshot>> loadActive(UUID tenantId);

    /** Loads one immutable historical revision without changing the active pointer. */
    Uni<Optional<VaultPolicySnapshot>> loadRevision(UUID tenantId, long revision);

    /**
     * Publishes revision expectedRevision + 1. Implementations must compare-and-set
     * the tenant head, persist rules and the administrative audit fact atomically.
     * A conflict or audit failure must leave the active revision unchanged.
     */

    Uni<VaultPolicySnapshot> publish(UUID tenantId, long expectedRevision,
                                     List<tech.kayys.numou.security.vault.domain.VaultPolicyRule> rules,
                                     String actor, String approver, Instant publishedAt,
                                     VaultPolicyChangeType changeType, Long sourceRevision);
}
