package tech.kayys.numou.history.source.application;

import tech.kayys.numou.history.source.domain.*;
import tech.kayys.numou.history.source.spi.HistoricalSourceRepository;
import tech.kayys.numou.history.source.spi.SourceIngestionRepository;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Records tenant-authorized ingestion requests and immutable extraction provenance.
 * Network fetching and OCR/transcription are performed by replaceable worker adapters.
 */
public final class SourceIngestionService {
    private final HistoricalSourceRepository sources;
    private final SourceIngestionRepository ingestions;
    private final HistoricalSourceTenantContext tenantContext;
    private final SourceIngestionAuthorizationPort authorization;
    private final SourceContentReferencePort contentReferences;
    private final Clock clock;

    public SourceIngestionService(
            HistoricalSourceRepository sources,
            SourceIngestionRepository ingestions,
            HistoricalSourceTenantContext tenantContext,
            SourceIngestionAuthorizationPort authorization,
            SourceContentReferencePort contentReferences,
            Clock clock) {
        this.sources = Objects.requireNonNull(sources);
        this.ingestions = Objects.requireNonNull(ingestions);
        this.tenantContext = Objects.requireNonNull(tenantContext);
        this.authorization = Objects.requireNonNull(authorization);
        this.contentReferences = Objects.requireNonNull(contentReferences);
        this.clock = Objects.requireNonNull(clock);
    }

    public SourceIngestionRequest requestDocumentIngestion(
            HistoricalSourceId sourceId, UUID documentId, UUID documentVersionId,
            String sha256, String contentType, String actorRef) {
        UUID tenant = tenant();
        Objects.requireNonNull(sourceId, "sourceId");
        authorization.requireCanRequestIngestion(tenant, sourceId.value(), actorRef);
        requireActiveSource(tenant, sourceId);
        contentReferences.requireCanReferenceVersion(tenant,
                Objects.requireNonNull(documentId, "documentId"),
                Objects.requireNonNull(documentVersionId, "documentVersionId"));
        var request = SourceIngestionRequest.fromDocument(sourceId, documentId, documentVersionId,
                sha256, contentType, actorRef, clock.instant());
        if (!ingestions.appendIngestion(tenant, request)) {
            throw new HistoricalSourceConflictException("An ingestion request with this identity already exists");
        }
        return request;
    }

    /**
     * Captures the canonical URI as an immutable request snapshot; this method performs no network access.
     * Workers must enforce SSRF protections, redirect limits, DNS/IP validation and response-size/time limits.
     */
    public SourceIngestionRequest requestCanonicalUriIngestion(HistoricalSourceId sourceId, String actorRef) {
        UUID tenant = tenant();
        Objects.requireNonNull(sourceId, "sourceId");
        authorization.requireCanRequestIngestion(tenant, sourceId.value(), actorRef);
        HistoricalSource source = requireActiveSource(tenant, sourceId);
        validateFetchableUri(source.canonicalUri());
        var request = SourceIngestionRequest.fromCanonicalUri(sourceId, source.canonicalUri(), actorRef, clock.instant());
        if (!ingestions.appendIngestion(tenant, request)) {
            throw new HistoricalSourceConflictException("An ingestion request with this identity already exists");
        }
        return request;
    }

    /** Records a worker-produced extraction without copying its payload into History. */
    public SourceExtractionArtifact recordExtraction(
            UUID ingestionId, SourceExtractionKind kind, String extractorId, String extractorVersion,
            UUID outputDocumentId, UUID outputDocumentVersionId, String inputSha256, String outputSha256,
            String languageTag, String locator, String workerRef) {
        UUID tenant = tenant();
        Objects.requireNonNull(ingestionId, "ingestionId");
        authorization.requireCanRecordExtraction(tenant, ingestionId, workerRef);
        SourceIngestionRequest request = ingestions.findIngestion(tenant, ingestionId)
                .orElseThrow(() -> new IllegalArgumentException("Source ingestion request not found"));
        requireActiveSource(tenant, request.sourceId());
        if (request.inputKind() == SourceIngestionInputKind.DOCUMENT_VERSION) {
            contentReferences.requireCanReferenceVersion(tenant,
                    request.inputDocumentId(), request.inputDocumentVersionId());
            if (inputSha256 == null || !request.inputSha256().equalsIgnoreCase(inputSha256)) {
                throw new IllegalArgumentException("Extraction input checksum does not match the requested document version");
            }
        }
        contentReferences.requireCanReferenceVersion(tenant,
                Objects.requireNonNull(outputDocumentId, "outputDocumentId"),
                Objects.requireNonNull(outputDocumentVersionId, "outputDocumentVersionId"));
        var artifact = SourceExtractionArtifact.create(ingestionId, kind, extractorId, extractorVersion,
                outputDocumentId, outputDocumentVersionId, inputSha256, outputSha256,
                languageTag, locator, workerRef, clock.instant());
        if (!ingestions.appendExtraction(tenant, artifact)) {
            throw new HistoricalSourceConflictException("Extraction provenance already exists");
        }
        return artifact;
    }

    public List<SourceIngestionRequest> listIngestions(HistoricalSourceId sourceId, int limit) {
        UUID tenant = tenant();
        Objects.requireNonNull(sourceId, "sourceId");
        validateLimit(limit);
        authorization.requireCanReadIngestions(tenant, sourceId.value());
        if (sources.find(tenant, sourceId).isEmpty()) return List.of();
        return List.copyOf(ingestions.listIngestions(tenant, sourceId.value(), limit));
    }

    public List<SourceExtractionArtifact> listExtractions(UUID ingestionId, int limit) {
        UUID tenant = tenant();
        Objects.requireNonNull(ingestionId, "ingestionId");
        validateLimit(limit);
        ingestions.findIngestion(tenant, ingestionId)
                .orElseThrow(() -> new IllegalArgumentException("Source ingestion request not found"));
        authorization.requireCanReadExtractions(tenant, ingestionId);
        return List.copyOf(ingestions.listExtractions(tenant, ingestionId, limit));
    }

    private HistoricalSource requireActiveSource(UUID tenant, HistoricalSourceId sourceId) {
        HistoricalSource source = sources.find(tenant, sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Historical source not found"));
        if (source.status() != HistoricalSourceStatus.ACTIVE) {
            throw new IllegalStateException("Archived sources cannot be ingested");
        }
        return source;
    }

    private static void validateFetchableUri(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Source must have a canonical HTTP(S) URI for URL ingestion");
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Canonical source URI is invalid", e);
        }
        String scheme = uri.getScheme();
        if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                || scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("URL ingestion requires an absolute HTTP(S) URI without user-info");
        }
    }

    private UUID tenant() {
        return Objects.requireNonNull(tenantContext.requireTenantId(), "tenant context returned null");
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
    }
}
