package tech.kayys.numou.content.document;
import tech.kayys.numou.content.spi.DocumentMediaUploadGateway;
import tech.kayys.numou.document.application.command.*;
import tech.kayys.numou.document.application.port.DocumentRepository;
import tech.kayys.numou.accounting.domain.document.*;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
/** Adapter delegates upload and immutable-version creation to the existing Document application. */
public final class DocumentMediaUploadAdapter implements DocumentMediaUploadGateway {
 private final CreateUploadSessionHandler create;private final CompleteUploadHandler complete;private final DocumentRepository documents;
 public DocumentMediaUploadAdapter(CreateUploadSessionHandler create,CompleteUploadHandler complete,DocumentRepository documents){this.create=Objects.requireNonNull(create);this.complete=Objects.requireNonNull(complete);this.documents=Objects.requireNonNull(documents);}
 @Override public CompletionStage<UploadTicket> begin(String tenantId,String fileName,String contentType,long size,String expectedSha256,Duration lifetime){
  var command=new CreateUploadSession(tenantId,DocumentType.GENERAL,DocumentClassification.INTERNAL,DocumentMetadata.of(fileName,contentType,size),expectedSha256,lifetime);
  return create.handle(command).map(result->new UploadTicket(result.uploadSessionId().value(),result.uploadUrl(),result.expiresAt(),result.requiredHeaders())).subscribeAsCompletionStage();
 }
 @Override public CompletionStage<UploadedDocument> complete(String tenantId,String uploadSessionId){
  var command=new CompleteUpload(tenantId,new UploadSessionId(uploadSessionId));
  return complete.handle(command).chain(result->documents.findById(tenantId,result.documentId())
   .onItem().transform(optional->optional.orElseThrow(()->new IllegalStateException("Completed Document is not readable for its tenant"))))
   .map(document->{var metadata=document.metadata();var version=document.latestVersion();return new UploadedDocument(document.id().value(),version.id().value(),metadata.filename(),metadata.contentType(),metadata.fileSize(),version.sha256Hash());})
   .subscribeAsCompletionStage();
 }
}
