package tech.kayys.numou.workspace.application.port;
import tech.kayys.numou.workspace.application.WorkspacePorts.AuditEntry;
import tech.kayys.numou.workspace.domain.PageComment;
import tech.kayys.numou.workspace.domain.PageCommentThread;
import java.time.Instant; import java.util.List; import java.util.UUID;
/** Transactional persistence boundary for comment writes and mention-delivery intents. */
public interface WorkspaceMentionOutboxStore {
 boolean createThreadWithMentions(PageCommentThread thread, AuditEntry audit, List<MentionIntent> mentions);
 boolean appendCommentWithMentions(UUID tenantId, UUID pageId, UUID threadId, PageComment comment, AuditEntry audit, List<MentionIntent> mentions);
 List<ClaimedMention> claimDue(String workerId, Instant now, Instant leaseUntil, int limit);
 boolean markDelivered(UUID tenantId, UUID deliveryId, UUID leaseToken, Instant now);
 boolean scheduleRetry(UUID tenantId, UUID deliveryId, UUID leaseToken, Instant availableAt, String error, Instant now);
 boolean markDead(UUID tenantId, UUID deliveryId, UUID leaseToken, String error, Instant now);
 List<DeadMention> listDead(UUID tenantId, int limit);
 boolean requeueDead(UUID tenantId, UUID deliveryId, UUID actorId, Instant now);
 record MentionIntent(UUID recipientId,String handle,String actorDisplayName,String pageTitle,String idempotencyKey) {
  public MentionIntent { java.util.Objects.requireNonNull(recipientId); if(handle==null||handle.isBlank())throw new IllegalArgumentException("handle required"); if(actorDisplayName==null||actorDisplayName.isBlank())throw new IllegalArgumentException("actorDisplayName required"); if(pageTitle==null)pageTitle=""; if(idempotencyKey==null||idempotencyKey.isBlank())throw new IllegalArgumentException("idempotencyKey required"); }
 }
 record ClaimedMention(UUID id,UUID tenantId,UUID pageId,UUID threadId,UUID commentId,UUID recipientId,UUID actorId,String handle,String actorDisplayName,String pageTitle,String idempotencyKey,int attempts,int retryAttempts,UUID leaseToken,Instant leaseUntil) {}
 record DeadMention(UUID id,UUID pageId,UUID commentId,UUID recipientId,int attempts,String lastError,Instant createdAt,Instant updatedAt) {}
}
