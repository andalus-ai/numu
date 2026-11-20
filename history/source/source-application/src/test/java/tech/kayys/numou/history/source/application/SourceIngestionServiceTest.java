package tech.kayys.numou.history.source.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.source.domain.*;
import tech.kayys.numou.history.source.spi.HistoricalSourceRepository;
import tech.kayys.numou.history.source.spi.SourceIngestionRepository;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SourceIngestionServiceTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String SHA = "a".repeat(64);
    private final HistoricalSource source = HistoricalSource.register(
        HistoricalSourceId.generate(), HistoricalSourceKind.BOOK, "Primary source", null,
        null, null, null, null, "en", Instant.parse("2026-11-20T00:00:00Z"));
    private final MemoryIngestions ingestions = new MemoryIngestions();
    private final SourceIngestionService service = new SourceIngestionService(
        sourceRepository(), ingestions, () -> TENANT, new SourceIngestionAuthorizationPort() {
            @Override public void requireCanIngestSource(UUID tenantId, HistoricalSourceId sourceId, String actor) {
                assertEquals(TENANT, tenantId);
                assertEquals(source.id(), sourceId);
                assertEquals("editor:1", actor);
            }
            @Override public void requireCanReadSourceIngestion(UUID tenantId, HistoricalSourceId sourceId) {
                assertEquals(TENANT, tenantId);
                assertEquals(source.id(), sourceId);
            }
        }, Clock.fixed(Instant.parse("2026-11-20T01:00:00Z"), ZoneOffset.UTC));

    @Test
    void idempotentReplayReturnsOriginalRequestAndRejectsChangedInputs() {
        SourceIngestionRequest first = request("request-1", "doc:original");
        SourceIngestionRequest replay = request("request-1", "doc:original");
        assertEquals(first.id(), replay.id());
        assertThrows(HistoricalSourceConflictException.class, () -> request("request-1", "doc:changed"));
    }

    @Test
    void recordsImmutableExtractionAttemptAgainstOriginalRequest() {
        SourceIngestionRequest request = request("request-2", "doc:original");
        SourceExtractionArtifact artifact = service.recordExtraction(
            request.id(), 1, SourceExtractionStatus.SUCCEEDED, "doc:extracted", SHA,
            "text/plain", "en", "page:1-3", null, null,
            Instant.parse("2026-11-20T00:30:00Z"));

        assertEquals(request.id(), artifact.requestId());
        assertEquals(source.id(), artifact.sourceId());
        assertEquals("doc:extracted", artifact.extractedDocumentRef());
        assertEquals("doc:original", ingestions.requests.get(request.id()).originalDocumentRef());
        assertEquals(List.of(artifact), service.listExtractions(request.id(), 10));
    }

    @Test
    void rejectsFailedAttemptThatPublishesExtractedContent() {
        assertThrows(IllegalArgumentException.class, () -> SourceExtractionArtifact.create(
            UUID.randomUUID(), source.id(), 1, SourceExtractionStatus.FAILED,
            "doc:should-not-exist", SHA, "text/plain", null, null, "OCR_FAILED",
            "provider failed", Instant.parse("2026-11-20T00:00:00Z"),
            Instant.parse("2026-11-20T00:01:00Z")));
    }

    private SourceIngestionRequest request(String key, String documentRef) {
        return service.request(source.id(), key, documentRef, SHA, "application/pdf",
            "ocr.provider-neutral", "1.0", "cfg:sha256:1", "editor:1");
    }

    private HistoricalSourceRepository sourceRepository() {
        return (HistoricalSourceRepository) Proxy.newProxyInstance(
            HistoricalSourceRepository.class.getClassLoader(),
            new Class<?>[]{HistoricalSourceRepository.class},
            (proxy, method, args) -> {
                if (method.getName().equals("find")) {
                    return Objects.equals(args[0], TENANT) && Objects.equals(args[1], source.id())
                        ? Optional.of(source) : Optional.empty();
                }
                if (method.getName().equals("toString")) return "HistoricalSourceRepositoryTestProxy";
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                throw new UnsupportedOperationException(method.getName());
            });
    }

    private final class MemoryIngestions implements SourceIngestionRepository {
        private final Map<UUID, SourceIngestionRequest> requests = new LinkedHashMap<>();
        private final Map<String, UUID> keys = new HashMap<>();
        private final Map<UUID, List<SourceExtractionArtifact>> artifacts = new HashMap<>();

        @Override public Optional<SourceIngestionRequest> findRequestByIdempotencyKey(
            UUID tenantId, HistoricalSourceId sourceId, String key) {
            UUID id = keys.get(tenantId + ":" + sourceId + ":" + key);
            return Optional.ofNullable(id).map(requests::get);
        }
        @Override public boolean appendRequest(UUID tenantId, SourceIngestionRequest request) {
            String key = tenantId + ":" + request.sourceId() + ":" + request.idempotencyKey();
            if (keys.containsKey(key)) return false;
            keys.put(key, request.id());
            requests.put(request.id(), request);
            return true;
        }
        @Override public Optional<SourceIngestionRequest> findRequest(UUID tenantId, UUID requestId) {
            return Optional.ofNullable(requests.get(requestId));
        }
        @Override public boolean appendExtraction(UUID tenantId, SourceExtractionArtifact artifact) {
            List<SourceExtractionArtifact> rows = artifacts.computeIfAbsent(artifact.requestId(), ignored -> new ArrayList<>());
            if (rows.stream().anyMatch(row -> row.attempt() == artifact.attempt())) return false;
            rows.add(artifact);
            return true;
        }
        @Override public List<SourceExtractionArtifact> listExtractions(UUID tenantId, UUID requestId, int limit) {
            return artifacts.getOrDefault(requestId, List.of()).stream().limit(limit).toList();
        }
    }
}
