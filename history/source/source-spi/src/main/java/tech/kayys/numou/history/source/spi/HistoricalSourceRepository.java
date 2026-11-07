package tech.kayys.numou.history.source.spi;
import tech.kayys.numou.history.source.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped persistence contract. Tenant IDs must originate from trusted host context. */
public interface HistoricalSourceRepository {
 boolean register(UUID tenantId, HistoricalSource source);
 Optional<HistoricalSource> find(UUID tenantId, HistoricalSourceId id);
 List<HistoricalSource> list(UUID tenantId, int limit);
 boolean revise(UUID tenantId, HistoricalSource revised, long expectedRevision);
 boolean createClaim(UUID tenantId, HistoricalClaim claim);
 Optional<HistoricalClaim> findClaim(UUID tenantId, HistoricalClaimId id);
 boolean appendEvidence(UUID tenantId, ClaimEvidence evidence);
 List<ClaimEvidence> listEvidence(UUID tenantId, HistoricalClaimId claimId, int limit);
}