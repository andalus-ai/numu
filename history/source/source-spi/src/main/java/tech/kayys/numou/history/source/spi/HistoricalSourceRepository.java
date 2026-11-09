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
 boolean appendAssessment(UUID tenantId, SourceAssessment assessment);
 List<SourceAssessment> listAssessments(UUID tenantId, HistoricalSourceId sourceId, int limit);
 Optional<SourceAssessment> findAssessment(UUID tenantId, UUID assessmentId);
 boolean appendAssessmentReview(UUID tenantId, SourceAssessmentReview review);
 List<SourceAssessmentReview> listAssessmentReviews(UUID tenantId, UUID assessmentId, int limit);
 boolean appendContributor(UUID tenantId, SourceContributor contributor);
 List<SourceContributor> listContributors(UUID tenantId, HistoricalSourceId sourceId, int limit);
 boolean appendEdition(UUID tenantId, SourceEdition edition);
 List<SourceEdition> listEditions(UUID tenantId, HistoricalSourceId sourceId, int limit);
 boolean appendHolding(UUID tenantId, ArchivalHolding holding);
 List<ArchivalHolding> listHoldings(UUID tenantId, HistoricalSourceId sourceId, int limit);
 Optional<ArchivalHolding> findHolding(UUID tenantId, HistoricalSourceId sourceId, UUID holdingId);
 boolean appendCatalogIdentifier(UUID tenantId, ArchivalCatalogIdentifier identifier);
 List<ArchivalCatalogIdentifier> listCatalogIdentifiers(UUID tenantId, HistoricalSourceId sourceId, UUID holdingId, int limit);
}