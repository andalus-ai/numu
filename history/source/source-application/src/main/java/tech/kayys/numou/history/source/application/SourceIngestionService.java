package tech.kayys.numou.history.source.application;

import tech.kayys.numou.history.source.domain.*;
import tech.kayys.numou.history.source.spi.HistoricalSourceRepository;
import tech.kayys.numou.history.source.spi.SourceIngestionRepository;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Application boundary for durable source-ingestion requests and immutable extraction provenance.
 * Fetching URLs, OCR, transcription and binary storage are deliberately delegated to adapters.
 */
public final class SourceIngestionService {
    private static final int MAX_RESULTS = 500;

    private final HistoricalSourceRepository sources;
    private final SourceIngestionRepository ingestions;
    private final HistoricalSourceTenantContext tenantContext;
    private final SourceIngestionAuthorizationPort authorization;
    private final Clock clock;

    public SourceIngestionService(
        HistoricalSourceRepository sources,
        SourceIngestionRepository ingestions,
        HistoricalSourceTenantContext tenantContext,
        SourceIngestionAuthorizationPort authorization,
        Clock clock
    ) {
        this.sources = Objects.requireNonNull(sources, "sources");
        this.ingestions = Objects.requireNonNull(ingestions, "ingestions");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Records an idempotent request against an existing shared-document reference.
     * A repeated key returns the original request only when all immutable inputs match.
     */
    public SourceIngestionRequest request(
        HistoricalSourceId sourceId, String idempotencyKey, String originalDocumentRef,
        String originalSha256, String mediaType, String extractorId, String extractorVersion,
        String configurationFingerprint, String requestedBy
    ) {
        UUID tenant = requireTenant();
        Objects.requireNonNull(sourceId, "sourceId");
        authorization.requireCanIngestSource(tenant, sourceId, requestedBy);
        if (sources.find(tenant, sourceId).isEmpty()) throw new IllegalArgumentException("Historical source not found");

        SourceIngestionRequest candidate = SourceIngestionRequest.create(sourceId, idempotencyKey,
            originalDocumentRef, originalSha256, mediaType, extractorId, extractorVersion,
            configurationFingerprint, requestedBy, clock.instant());

        var existing = ingestions.findRequestByIdempotencyKey(tenant, sourceId, candidate.idempotencyKey());
        if (existing.isPresent()) {
            ensureSameRequest(existing.get(), candidate);
            return existing.get();
        }
        if (ingestions.appendRequest(tenant, candidate)) return candidate;

        // Resolve a concurrent winner after the unique-key race; never silently accept changed inputs.
        var winner = ingestions.findRequestByIdempotencyKey(tenant, sourceId, candidate.idempotencyKey())
            .orElseThrow(() -> new HistoricalSourceConflictException("Ingestion request could not be recorded"));
        ensureSameRequest(winner, candidate);
        return winner;
    }

    public SourceExtractionArtifact recordExtraction(
        UUID requestId, int attempt, SourceExtractionStatus status,
        String extractedDocumentRef, String extractedSha256, String extractedMediaType,
        String languageTag, String locator, String failureCode, String failureDetail,
        java.time.Instant startedAt
    ) {
        UUID tenant = requireTenant();
        Objects.requireNonNull(requestId, "requestId");
        SourceIngestionRequest request = ingestions.findRequest(tenant, requestId)
            .orElseThrow(() -> new IllegalArgumentException("Source ingestion request not found"));
        authorization.requireCanIngestSource(tenant, request.sourceId(), request.requestedBy());
        SourceExtractionArtifact artifact = SourceExtractionArtifact.create(requestId, request.sourceId(),
            attempt, status, extractedDocumentRef, extractedSha256, extractedMediaType,
            languageTag, locator, failureCode, failureDetail, startedAt, clock.instant());
        if (!ingestions.appendExtraction(tenant, artifact)) {
            throw new HistoricalSourceConflictException("Extraction attempt already exists or could not be appended");
        }
        return artifact;
    }

    public List<SourceExtractionArtifact> listExtractions(UUID requestId, int limit) {
        UUID tenant = requireTenant();
        Objects.requireNonNull(requestId, "requestId");
        if (limit < 1 || limit > MAX_RESULTS) throw new IllegalArgumentException("limit must be between 1 and " + MAX_RESULTS);
        SourceIngestionRequest request = ingestions.findRequest(tenant, requestId)
            .orElseThrow(() -> new IllegalArgumentException("Source ingestion request not found"));
        authorization.requireCanReadSourceIngestion(tenant, request.sourceId());
        return List.copyOf(ingestions.listExtractions(tenant, requestId, limit));
    }

    private UUID requireTenant() {
        return Objects.requireNonNull(tenantContext.requireTenantId(), "trusted tenant context is required");
    }

    private static void ensureSameRequest(SourceIngestionRequest existing, SourceIngestionRequest candidate) {
        boolean same = existing.sourceId().equals(candidate.sourceId())
            && existing.originalDocumentRef().equals(candidate.originalDocumentRef())
            && existing.originalSha256().equals(candidate.originalSha256())
            && existing.mediaType().equals(candidate.mediaType())
            && existing.extractorId().equals(candidate.extractorId())
            && existing.extractorVersion().equals(candidate.extractorVersion())
            && existing.configurationFingerprint().equals(candidate.configurationFingerprint())
            && existing.requestedBy().equals(candidate.requestedBy());
        if (!same) throw new HistoricalSourceConflictException("Idempotency key was already used with different ingestion inputs");
    }
}
