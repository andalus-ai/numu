package tech.kayys.numou.workspace.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseRecordTest {
    private final UUID tenant = UUID.randomUUID();
    private final UUID database = UUID.randomUUID();
    private final UUID tableId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    private DatabaseTable table() {
        return new DatabaseTable(tableId, tenant, database, "Tasks", 1, List.of(
                new DatabaseProperty(UUID.randomUUID(), "title", "Title", DatabasePropertyType.TEXT, true, List.of(), null),
                new DatabaseProperty(UUID.randomUUID(), "estimate", "Estimate", DatabasePropertyType.NUMBER, false, List.of(), null),
                new DatabaseProperty(UUID.randomUUID(), "status", "Status", DatabasePropertyType.SELECT, true,
                        List.of("todo", "doing", "done"), null),
                new DatabaseProperty(UUID.randomUUID(), "due_date", "Due date", DatabasePropertyType.DATE, false, List.of(), null),
                new DatabaseProperty(UUID.randomUUID(), "labels", "Labels", DatabasePropertyType.MULTI_SELECT, false,
                        List.of("urgent", "backend"), null)
        ));
    }

    @Test
    void validatesAndCanonicalizesRecordValues() {
        DatabaseRecord record = DatabaseRecord.create(tenant, database, table(), Map.of(
                "title", "Ship M3",
                "estimate", 2,
                "status", "doing",
                "due_date", "2026-02-01",
                "labels", List.of("backend", "urgent", "backend")
        ), now);

        assertEquals(1, record.revision());
        assertEquals(new BigDecimal("2"), record.values().get("estimate"));
        assertEquals(LocalDate.parse("2026-02-01"), record.values().get("due_date"));
        assertEquals(List.of("backend", "urgent"), record.values().get("labels"));
    }

    @Test
    void rejectsMissingRequiredAndUnknownProperties() {
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseRecord.create(tenant, database, table(), Map.of("status", "todo"), now));
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseRecord.create(tenant, database, table(), Map.of("title", "x", "status", "todo", "extra", true), now));
    }

    @Test
    void rejectsInvalidSelectOptionsAndTenantMismatch() {
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseRecord.create(tenant, database, table(), Map.of("title", "x", "status", "unknown"), now));
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseRecord.create(UUID.randomUUID(), database, table(), Map.of("title", "x", "status", "todo"), now));
    }

    @Test
    void updateRequiresExpectedRevisionAndCreatesNextImmutableRevision() {
        DatabaseTable table = table();
        DatabaseRecord first = DatabaseRecord.create(tenant, database, table,
                Map.of("title", "Draft", "status", "todo"), now);
        DatabaseRecord second = first.update(table, 1,
                Map.of("title", "Ready", "status", "done"), now.plusSeconds(5));

        assertEquals(first.id(), second.id());
        assertEquals(2, second.revision());
        assertEquals("Draft", first.values().get("title"));
        assertEquals("Ready", second.values().get("title"));
        assertThrows(IllegalStateException.class,
                () -> first.update(table, 0, Map.of("title", "Conflict", "status", "todo"), now.plusSeconds(10)));
    }

    @Test
    void propertySchemaRejectsInvalidOptionDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseProperty(UUID.randomUUID(),
                "status", "Status", DatabasePropertyType.SELECT, false, List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseProperty(UUID.randomUUID(),
                "status", "Status", DatabasePropertyType.SELECT, false, List.of("todo", "todo"), null));
    }
}
