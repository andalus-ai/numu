package tech.kayys.numou.content.spi;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletionStage;
/** Boundary to Numou Document's authenticated upload-session and immutable-version workflow. */
public interface DocumentMediaUploadGateway {
 CompletionStage<UploadTicket> begin(String tenantId,String fileName,String contentType,long size,String expectedSha256,Duration lifetime);
 CompletionStage<UploadedDocument> complete(String tenantId,String uploadSessionId);
 record UploadTicket(String uploadSessionId,String uploadUrl,Instant expiresAt,Map<String,String> requiredHeaders){
  public UploadTicket{requiredHeaders=requiredHeaders==null?Map.of():Map.copyOf(requiredHeaders);}
 }
 record UploadedDocument(String documentId,String documentVersionId,String fileName,String contentType,long size,String sha256){}
}
