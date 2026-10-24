package tech.kayys.numou.workspace.persistence.jdbc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JdbcStructuredDatabaseRepositoryJsonTest {

    @Test
    void roundTripsCanonicalWorkspaceValuesAndEscapedText() {
        UUID relationId = UUID.randomUUID();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("title", "line 1\nquote: \" and slash \\");
        input.put("amount", new BigDecimal("1200.050"));
        input.put("enabled", true);
        input.put("dueDate", LocalDate.parse("2026-10-24"));
        input.put("updatedAt", Instant.parse("2026-10-24T03:04:05Z"));
        input.put("relation", List.of(relationId));
        input.put("nested", Map.of("nil", "literal null", "count", 2));

        Object decoded = JdbcStructuredDatabaseRepository.Json.read(
                JdbcStructuredDatabaseRepository.Json.write(input));

        assertInstanceOf(Map.class, decoded);
        Map<?, ?> values = (Map<?, ?>) decoded;
        assertEquals(input.get("title"), values.get("title"));
        assertEquals(new BigDecimal("1200.050"), values.get("amount"));
        assertEquals(Boolean.TRUE, values.get("enabled"));
        assertEquals("2026-10-24", values.get("dueDate"));
        assertEquals("2026-10-24T03:04:05Z", values.get("updatedAt"));
        assertEquals(List.of(relationId.toString()), values.get("relation"));
        assertEquals(Map.of("nil", "literal null", "count", new BigDecimal("2")),
                values.get("nested"));
    }

    @Test
    void supportsJsonNullAndDetectsDuplicateKeysEvenWhenFirstValueIsNull() {
        Object decoded = JdbcStructuredDatabaseRepository.Json.read("{\"value\":null}");
        assertInstanceOf(Map.class, decoded);
        assertTrue(((Map<?, ?>) decoded).containsKey("value"));
        assertNull(((Map<?, ?>) decoded).get("value"));
        assertThrows(IllegalArgumentException.class,
                () -> JdbcStructuredDatabaseRepository.Json.read("{\"value\":null,\"value\":1}"));
    }

    @Test
    void rejectsMalformedJsonNumbersAndContainers() {
        for (String malformed : List.of(
                "01", "-", "--1", "+1", "1.", "1e", "1e+", "[1,]", "{\"a\":1,}",
                "\"\\x\"", "{\"a\":1} trailing")) {
            assertThrows(IllegalArgumentException.class,
                    () -> JdbcStructuredDatabaseRepository.Json.read(malformed),
                    () -> "should reject malformed JSON: " + malformed);
        }
    }

    @Test
    void rejectsUnsupportedJavaValuesWhenWritingJson() {
        assertThrows(IllegalArgumentException.class,
                () -> JdbcStructuredDatabaseRepository.Json.write(new Object()));
    }
}
