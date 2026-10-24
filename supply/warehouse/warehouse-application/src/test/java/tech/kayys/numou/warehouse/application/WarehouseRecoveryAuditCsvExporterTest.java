package tech.kayys.numou.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WarehouseRecoveryAuditCsvExporterTest {

    @Test
    void exportsBoundedPagesAndReturnsContinuationCursor() throws IOException {
        FakeRepository repository = new FakeRepository(List.of(
                event("tenant-a", "cmd-3", "ordinary"),
                event("tenant-a", "cmd-2", "line one, line two"),
                event("tenant-a", "cmd-1", "=SUM(A1:A2)")));
        WarehouseRecoveryAuditCsvExporter exporter = new WarehouseRecoveryAuditCsvExporter(
                new WarehouseRecoveryOperationsService(repository));
        StringBuilder csv = new StringBuilder();

        var first = exporter.export("tenant-a", 2, 2, null, csv);

        assertEquals(2, first.exportedCount());
        assertFalse(first.complete());
        assertTrue(first.nextCursor() != null);
        assertTrue(csv.toString().contains("\"line one, line two\""));
        assertTrue(csv.toString().startsWith("tenant_id,command_key,action"));
        assertEquals(2, repository.requestedLimits.get(0));

        StringBuilder lastCsv = new StringBuilder();
        var second = exporter.export("tenant-a", 2, 2, first.nextCursor(), lastCsv);
        assertEquals(1, second.exportedCount());
        assertTrue(second.complete());
        assertTrue(lastCsv.toString().contains("\"'=SUM(A1:A2)\""));
    }

    @Test
    void rejectsInvalidBoundsAndTenantLeakage() {
        WarehouseRecoveryAuditCsvExporter exporter = new WarehouseRecoveryAuditCsvExporter(
                new WarehouseRecoveryOperationsService(new FakeRepository(List.of(event("other", "cmd", "x")))));
        assertThrows(IllegalArgumentException.class,
                () -> exporter.export("tenant-a", 0, 1, null, new StringBuilder()));
        assertThrows(IllegalStateException.class,
                () -> exporter.export("tenant-a", 1, 1, null, new StringBuilder()));
    }

    @Test
    void escapesQuotesAndNullCorrelation() throws IOException {
        FakeRepository repository = new FakeRepository(List.of(event("tenant-a", "cmd-\"quoted", "reason \"quoted\"")));
        StringBuilder csv = new StringBuilder();
        new WarehouseRecoveryAuditCsvExporter(new WarehouseRecoveryOperationsService(repository))
                .export("tenant-a", 10, 10, null, csv);
        assertTrue(csv.toString().contains("\"cmd-\"\"quoted\""));
        assertTrue(csv.toString().contains("\"reason \"\"quoted\"\"\""));
        assertTrue(csv.toString().contains(",\"\",\"2026-01-01T00:00:00Z\""));
    }

    private static WarehouseRecoveryOperatorAuditEvent event(String tenant, String command, String reason) {
        return new WarehouseRecoveryOperatorAuditEvent(tenant, command, "DEAD_LETTER_REPLAY",
                "operator-1", reason, 2, 3, null, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static final class FakeRepository implements WarehouseExecutionAttemptRepository {
        private final List<WarehouseRecoveryOperatorAuditEvent> events;
        private final List<Integer> requestedLimits = new ArrayList<>();

        private FakeRepository(List<WarehouseRecoveryOperatorAuditEvent> events) {
            this.events = events;
        }

        @Override public WarehouseExecutionAttempt createIfAbsent(WarehouseExecutionAttempt attempt) { throw new UnsupportedOperationException(); }
        @Override public Optional<WarehouseExecutionAttempt> find(String tenantId, String commandKey) { throw new UnsupportedOperationException(); }
        @Override public WarehouseExecutionAttempt transition(String tenantId, String commandKey, long expectedVersion, WarehouseExecutionAttempt candidate) { throw new UnsupportedOperationException(); }
        @Override public List<WarehouseExecutionAttempt> findRecoverable(int limit) { throw new UnsupportedOperationException(); }

        @Override
        public WarehouseRecoveryAuditPage pageOperatorAuditEvents(String tenantId, int limit, String cursor) {
            requestedLimits.add(limit);
            int start = cursor == null ? 0 : Integer.parseInt(cursor);
            List<WarehouseRecoveryOperatorAuditEvent> selected = events.subList(start, Math.min(start + limit, events.size()));
            int end = start + selected.size();
            return new WarehouseRecoveryAuditPage(selected, end < events.size() ? Integer.toString(end) : null);
        }
    }
}
