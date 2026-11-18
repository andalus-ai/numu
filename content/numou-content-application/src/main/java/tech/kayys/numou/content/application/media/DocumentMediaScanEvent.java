package tech.kayys.numou.content.application.media;
import java.util.Objects;
/** Trusted event projection from Document's version-scoped malware scan completion event. */
public record DocumentMediaScanEvent(String tenantId,String documentId,String documentVersionId,String verdict) {
 public DocumentMediaScanEvent {
  if(tenantId==null||tenantId.isBlank()||documentId==null||documentId.isBlank()||documentVersionId==null||documentVersionId.isBlank())throw new IllegalArgumentException("tenantId, documentId and documentVersionId are required");
  Objects.requireNonNull(verdict);
  if(!verdict.equals("CLEAN")&&!verdict.equals("INFECTED")&&!verdict.equals("FAILED"))throw new IllegalArgumentException("Unsupported Document scan verdict");
 }
}
