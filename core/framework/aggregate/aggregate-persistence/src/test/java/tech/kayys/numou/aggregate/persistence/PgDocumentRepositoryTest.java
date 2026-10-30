package tech.kayys.numou.aggregate.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PgDocumentRepositoryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    record Snapshot(String value) { }

    @Test
    void decodesCanonicalJsonObject() throws Exception {
        Snapshot snapshot = PgDocumentRepository.decodeSnapshot("{\"value\":\"wallet\"}", Snapshot.class, mapper);

        assertEquals("wallet", snapshot.value());
    }

    @Test
    void decodesLegacyJsonStringContainingObject() throws Exception {
        Snapshot snapshot = PgDocumentRepository.decodeSnapshot(
                "\"{\\\"value\\\":\\\"wallet\\\"}\"", Snapshot.class, mapper);

        assertEquals("wallet", snapshot.value());
    }

    @Test
    void rejectsNonObjectDocument() {
        assertThrows(IllegalStateException.class,
                () -> PgDocumentRepository.decodeSnapshot("[1,2,3]", Snapshot.class, mapper));
    }
}
