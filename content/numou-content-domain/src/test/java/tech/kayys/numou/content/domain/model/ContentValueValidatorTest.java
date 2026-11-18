package tech.kayys.numou.content.domain.model;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.content.domain.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ContentValueValidatorTest {
    private final ContentValueValidator validator = new ContentValueValidator();
    private ContentTypeSchema schema() {
        return ContentTypeSchema.create(new TenantId(UUID.randomUUID()), ContentTypeId.newId(),
                "article", "Article", List.of(
                new ContentFieldDefinition("title", FieldType.STRING, true, false, 3, 100, null, null, null, List.of()),
                new ContentFieldDefinition("status", FieldType.STRING, false, false, null, 20, null, null, null, List.of("draft", "review")),
                new ContentFieldDefinition("views", FieldType.INTEGER, false, false, null, null, BigDecimal.ZERO, BigDecimal.valueOf(1000), null, List.of()),
                new ContentFieldDefinition("published_at", FieldType.DATETIME, false, true, null, null, null, null, null, List.of())
        ), Instant.parse("2026-01-01T00:00:00Z"));
    }
    @Test void acceptsValidValues() {
        var result = validator.validate(schema(), Map.of("title", "Numou CMS", "status", "draft", "views", 12));
        assertTrue(result.valid(), result.violations().toString());
    }
    @Test void reportsMissingWrongTypeUnknownAndOutOfRangeFields() {
        var result = validator.validate(schema(), Map.of("views", 5000L, "extra", true, "title", "x"));
        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(v -> v.code().equals("unknown_field")));
        assertTrue(result.violations().stream().anyMatch(v -> v.code().equals("min_length")));
        assertTrue(result.violations().stream().anyMatch(v -> v.code().equals("max_number")));
    }
    @Test void validatesNullabilityAndStructuredJson() {
        var s = ContentTypeSchema.create(new TenantId(UUID.randomUUID()), ContentTypeId.newId(),
                "page", "Page", List.of(
                new ContentFieldDefinition("metadata", FieldType.JSON, false, false, null, null, null, null, null, List.of())
        ), Instant.now());
        assertTrue(validator.validate(s, Map.of("metadata", Map.of("x", 1))).valid());
        assertTrue(validator.validate(s, new java.util.HashMap<>() {{ put("metadata", null); }}).violations()
                .stream().anyMatch(v -> v.code().equals("null_not_allowed")));
    }
    @Test void publishedSchemasAreImmutableSnapshots() {
        var first = schema().publish(Instant.parse("2026-01-02T00:00:00Z"));
        var second = first.nextDraft(List.of(new ContentFieldDefinition("title", FieldType.TEXT, true, false,
                null, 500, null, null, null, List.of())), Instant.parse("2026-01-03T00:00:00Z"));
        assertEquals(1, first.version());
        assertEquals(2, second.version());
        assertEquals(SchemaStatus.PUBLISHED, first.status());
        assertEquals(SchemaStatus.DRAFT, second.status());
    }
}
