package tech.kayys.numou.content.application.media;
import tech.kayys.numou.content.application.ContentCommandContext;
import tech.kayys.numou.content.domain.media.MediaAsset;
import tech.kayys.numou.content.domain.media.MediaKind;
import tech.kayys.numou.content.spi.DocumentMediaUploadGateway;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
/** Orchestrates binary upload through Document; CMS only registers the completed immutable version. */
public final class MediaUploadApplicationService {
 private final DocumentMediaUploadGateway documents;private final MediaApplicationService media;
 public MediaUploadApplicationService(DocumentMediaUploadGateway documents,MediaApplicationService media){this.documents=Objects.requireNonNull(documents);this.media=Objects.requireNonNull(media);}
 public CompletionStage<DocumentMediaUploadGateway.UploadTicket> begin(ContentCommandContext context,String fileName,String contentType,MediaKind kind,long size,String expectedSha256,Duration lifetime){
  Objects.requireNonNull(kind);if(expectedSha256==null||!expectedSha256.matches("(?i)[0-9a-f]{64}"))throw new IllegalArgumentException("CMS media uploads require an expected SHA-256 checksum");
  return documents.begin(context.tenantId().toString(),fileName,contentType,kind.name(),size,expectedSha256,lifetime);
 }
 public CompletionStage<MediaAsset> complete(ContentCommandContext context,String uploadSessionId){
  return documents.complete(context.tenantId().toString(),uploadSessionId).thenApply(uploaded->{
   if(uploaded.size()<0||uploaded.sha256()==null||!uploaded.sha256().matches("(?i)[0-9a-f]{64}"))throw new IllegalStateException("Document completion returned invalid binary metadata");
   return media.registerUploadedDocument(context,uploaded.documentId(),uploaded.documentVersionId(),uploaded.fileName(),uploaded.contentType(),MediaKind.valueOf(uploaded.mediaKind()),uploaded.size(),uploaded.sha256());
  });
 }
}
