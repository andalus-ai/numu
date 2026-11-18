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
    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");
    private static final String INPUT_HASH = "a".repeat(64);
    private static final String OUTPUT_HASH = "b".repeat(64);

    @Test
    void documentIngestionRecordsExtractionAgainstSharedDocumentVersions() {
        UUID tenant = UUID.randomUUID();
        UUID inputDocument = UUID.randomUUID();
        UUID inputVersion = UUID.randomUUID();
        UUID outputDocument = UUID.randomUUID();
        UUID outputVersion = UUID.randomUUID();
        var source = HistoricalSource.register(HistoricalSourceId.generate(), HistoricalSourceKind.MANUSCRIPT,
                "Archive scan", null, null, null, "Archive", "c. 1890", "id", NOW);
        var state = new RepositoryState(source);
        var references = new ArrayList<String>();
        var service = service(tenant, source, state, references);

        var request = service.requestDocumentIngestion(source.id(), inputDocument, inputVersion,
                INPUT_HASH, "application/pdf", "actor:researcher");
        var extraction = service.recordExtraction(request.id(), SourceExtractionKind.OCR_TEXT,
                "ocr-engine", "2.1.0", outputDocument, outputVersion, INPUT_HASH, OUTPUT_HASH,
                "id-id", "page:12", "worker:ocr-1");

        assertEquals(inputDocument, request.inputDocumentId());
        assertEquals(inputVersion, request.inputDocumentVersionId());
        assertEquals(request.id(), extraction.ingestionId());
        assertEquals("id-ID", extraction.languageTag());
        assertEquals(1, service.listIngestions(source.id(), 10).size());
        assertEquals(1, service.listExtractions(request.id(), 10).size());
        assertEquals(3, references.size(), "input is checked at request and extraction; output is checked before linking");
    }

    @Test
    void uriIngestionSnapshotsCanonicalUriAndRejectsChecksumMismatch() {
        UUID tenant = UUID.randomUUID();
        var source = HistoricalSource.register(HistoricalSourceId.generate(), HistoricalSourceKind.WEBSITE,
                "Archive portal", null, null, "https://archive.example/catalogue/42",
                null, null, "en", NOW);
        var state = new RepositoryState(source);
        var service = service(tenant, source, state, new ArrayList<>());

        var request = service.requestCanonicalUriIngestion(source.id(), "actor:researcher");
        assertEquals("https://archive.example/catalogue/42", request.inputUri());
        assertEquals(SourceIngestionInputKind.CANONICAL_URI, request.inputKind());

        assertThrows(IllegalArgumentException.class, () -> service.recordExtraction(request.id(),
                SourceExtractionKind.TEXT_EXTRACTION, "extractor", "1",
                UUID.randomUUID(), UUID.randomUUID(), "wrong", OUTPUT_HASH,
                "en", null, "worker:1"));
    }

    private static SourceIngestionService service(
            UUID tenant, HistoricalSource source, RepositoryState state, List<String> references) {
        HistoricalSourceRepository sourceRepository = (HistoricalSourceRepository) Proxy.newProxyInstance(
                HistoricalSourceRepository.class.getClassLoader(),
                new Class<?>[]{HistoricalSourceRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("find")) return Optional.of(source);
                    if (method.getName().equals("list")) return List.of(source);
                    if (method.getReturnType().equals(boolean.class)) return false;
                    if (method.getReturnType().equals(Optional.class)) return Optional.empty();
                    if (method.getReturnType().equals(List.class)) return List.of();
                    return null;
                });

        SourceIngestionRepository ingestionRepository = (SourceIngestionRepository) Proxy.newProxyInstance(
                SourceIngestionRepository.class.getClassLoader(),
                new Class<?>[]{SourceIngestionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "appendIngestion" -> {
                        var request = (SourceIngestionRequest) args[1];
                        state.requests.put(request.id(), request);
                        yield true;
                    }
                    case "findIngestion" -> Optional.ofNullable(state.requests.get((UUID) args[1]));
                    case "listIngestions" -> state.requests.values().stream()
                            .filter(request -> request.sourceId().value().equals(args[1])).toList();
                    case "appendExtraction" -> {
                        state.artifacts.add((SourceExtractionArtifact) args[1]);
                        yield true;
                    }
                    case "listExtractions" -> state.artifacts.stream()
                            .filter(artifact -> artifact.ingestionId().equals(args[1])).toList();
                    default -> throw new UnsupportedOperationException(method.getName());
                });

        SourceIngestionAuthorizationPort authorization = new SourceIngestionAuthorizationPort() {
            public void requireCanRequestIngestion(UUID t, UUID s, String actor) {}
            public void requireCanReadIngestions(UUID t, UUID s) {}
            public void requireCanRecordExtraction(UUID t, UUID i, String worker) {}
            public void requireCanReadExtractions(UUID t, UUID i) {}
        };
        SourceContentReferencePort contentReferences = (t, documentId, versionId) ->
                references.add(documentId + ":" + versionId);

        return new SourceIngestionService(sourceRepository, ingestionRepository, () -> tenant,
                authorization, contentReferences, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class RepositoryState {
        private final Map<UUID, SourceIngestionRequest> requests = new LinkedHashMap<>();
        private final List<SourceExtractionArtifact> artifacts = new ArrayList<>();
        private RepositoryState(HistoricalSource ignored) {}
    }
}
