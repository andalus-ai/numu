package tech.kayys.numou.history.source.application;
import tech.kayys.numou.history.source.domain.*;
import java.util.UUID;
/** Host-owned authorization. Implementations must check actor permissions and referenced resources. */
public interface HistoricalSourceAuthorizationPort {
 void requireCanRegisterSource(UUID tenantId);
 void requireCanReadSource(UUID tenantId, HistoricalSourceId sourceId);
 void requireCanReviseSource(UUID tenantId, HistoricalSourceId sourceId);
 void requireCanCreateClaim(UUID tenantId);
 void requireCanReferenceSubject(UUID tenantId, String subjectKind, String subjectId);
 void requireCanReadClaim(UUID tenantId, HistoricalClaimId claimId);
 void requireCanAttachEvidence(UUID tenantId, HistoricalClaimId claimId, HistoricalSourceId sourceId);
 void requireCanAssessSource(UUID tenantId, HistoricalSourceId sourceId, String assessorRef);
 void requireCanReviewSourceAssessment(UUID tenantId, HistoricalSourceId sourceId, UUID assessmentId, String reviewerRef);
 void requireCanManageSourceBibliography(UUID tenantId, HistoricalSourceId sourceId);
 void requireCanReadSourceBibliography(UUID tenantId, HistoricalSourceId sourceId);
}