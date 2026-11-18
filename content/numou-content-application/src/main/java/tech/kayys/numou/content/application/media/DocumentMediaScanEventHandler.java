package tech.kayys.numou.content.application.media;
import tech.kayys.numou.content.domain.TenantId;
import tech.kayys.numou.content.domain.media.MediaAsset;
import tech.kayys.numou.content.domain.media.MediaScanStatus;
import java.util.Objects;
/** Idempotent consumer-side handler; broker acknowledgement belongs to the host runtime. */
public final class DocumentMediaScanEventHandler {
 private final MediaApplicationService media;
 public DocumentMediaScanEventHandler(MediaApplicationService media){this.media=Objects.requireNonNull(media);}
 public MediaAsset handle(DocumentMediaScanEvent event){
  Objects.requireNonNull(event);
  return media.applyDocumentScanVerdict(TenantId.of(event.tenantId()),event.documentId(),event.documentVersionId(),MediaScanStatus.valueOf(event.verdict()));
 }
}
