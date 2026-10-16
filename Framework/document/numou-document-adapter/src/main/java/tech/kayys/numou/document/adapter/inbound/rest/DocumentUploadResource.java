package tech.kayys.syirkah.document.adapter.inbound.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.syirkah.accounting.domain.document.DocumentClassification;
import tech.kayys.syirkah.accounting.domain.document.DocumentMetadata;
import tech.kayys.syirkah.accounting.domain.document.DocumentType;
import tech.kayys.syirkah.accounting.domain.document.UploadSessionId;
import tech.kayys.syirkah.document.application.command.CompleteUpload;
import tech.kayys.syirkah.document.application.command.CompleteUploadHandler;
import tech.kayys.syirkah.document.application.command.CreateUploadSession;
import tech.kayys.syirkah.document.application.command.CreateUploadSessionHandler;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/tenants/{tenantId}/documents")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DocumentUploadResource {
    private final CreateUploadSessionHandler createUploadSession;
    private final CompleteUploadHandler completeUpload;

    @Inject
    public DocumentUploadResource(
            CreateUploadSessionHandler createUploadSession,
            CompleteUploadHandler completeUpload
    ) {
        this.createUploadSession = createUploadSession;
        this.completeUpload = completeUpload;
    }

    @POST
    @Path("/upload-sessions")
    public Uni<Response> createUploadSession(
            @PathParam("tenantId") UUID tenantId,
            CreateUploadSessionRequest request
    ) {
        if (request == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Request body is required"));
        }
        try {
            if (request.documentType() == null || request.classification() == null) {
                throw new IllegalArgumentException("documentType and classification are required");
            }
            if (request.filename() == null || request.filename().isBlank()
                    || request.contentType() == null || request.contentType().isBlank()) {
                throw new IllegalArgumentException("filename and contentType are required");
            }
            if (request.fileSize() == null || request.fileSize() < 0) {
                throw new IllegalArgumentException("fileSize is required and cannot be negative");
            }
            if (request.lifetimeSeconds() != null && request.lifetimeSeconds() <= 0) {
                throw new IllegalArgumentException("lifetimeSeconds must be positive");
            }
            var metadata = new DocumentMetadata(
                    request.filename(),
                    request.contentType(),
                    request.fileSize(),
                    request.customAttributes()
            );
            var command = new CreateUploadSession(
                    tenantId.toString(),
                    request.documentType(),
                    request.classification(),
                    metadata,
                    request.expectedSha256(),
                    request.lifetimeSeconds() == null ? null : Duration.ofSeconds(request.lifetimeSeconds())
            );
            return createUploadSession.handle(command).map(result -> Response.created(
                            URI.create("/api/v1/tenants/" + tenantId + "/documents/upload-sessions/"
                                    + result.uploadSessionId().value())
                    )
                    .entity(new UploadTargetResponse(
                            result.uploadSessionId().value(),
                            result.uploadUrl(),
                            result.expiresAt(),
                            result.requiredHeaders()
                    ))
                    .build());
        } catch (RuntimeException invalidRequest) {
            return Uni.createFrom().failure(invalidRequest);
        }
    }

    @POST
    @Path("/upload-sessions/{uploadSessionId}/complete")
    public Uni<CompleteUploadResponse> completeUpload(
            @PathParam("tenantId") UUID tenantId,
            @PathParam("uploadSessionId") UUID uploadSessionId
    ) {
        return completeUpload.handle(new CompleteUpload(
                        tenantId.toString(),
                        new UploadSessionId(uploadSessionId.toString())
                ))
                .map(result -> new CompleteUploadResponse(
                        result.documentId().value(),
                        result.versionId().value(),
                        result.versionNumber()
                ));
    }

    public record CreateUploadSessionRequest(
            DocumentType documentType,
            DocumentClassification classification,
            String filename,
            String contentType,
            Long fileSize,
            String expectedSha256,
            Long lifetimeSeconds,
            Map<String, String> customAttributes
    ) {}

    public record UploadTargetResponse(
            String uploadSessionId,
            String uploadUrl,
            java.time.Instant expiresAt,
            Map<String, String> requiredHeaders
    ) {}

    public record CompleteUploadResponse(String documentId, String versionId, int versionNumber) {}
}
