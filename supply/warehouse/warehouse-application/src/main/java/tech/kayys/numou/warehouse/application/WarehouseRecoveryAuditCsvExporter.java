package tech.kayys.numou.warehouse.application;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Bounded, streaming CSV export for tenant-scoped recovery audit events. */
public final class WarehouseRecoveryAuditCsvExporter {
    private static final String HEADER = "tenant_id,command_key,action,actor_id,reason,prior_version,resulting_version,correlation_id,occurred_at";
    private final WarehouseRecoveryOperationsService operations;

    public WarehouseRecoveryAuditCsvExporter(WarehouseRecoveryOperationsService operations) {
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    /**
     * Writes at most maxEvents rows directly to destination without retaining the full history.
     * A non-null cursor in the result means more records remain. Each output chunk includes a header.
     * Authentication, authorization, and trusted tenant resolution belong at the API boundary.
     */
    public ExportResult export(String tenantId, int pageSize, int maxEvents, String cursor,
                               Appendable destination) throws IOException {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (pageSize < 1 || pageSize > 500) throw new IllegalArgumentException("pageSize must be between 1 and 500");
        if (maxEvents < 1 || maxEvents > 100_000) throw new IllegalArgumentException("maxEvents must be between 1 and 100000");
        if (cursor != null && (cursor.isBlank() || cursor.length() > 200)) {
            throw new IllegalArgumentException("cursor must be null or contain 1-200 characters");
        }
        Objects.requireNonNull(destination, "destination");
        destination.append(HEADER).append('\n');
        int exported = 0;
        String next = cursor;
        Set<String> seenCursors = new HashSet<>();
        if (next != null) seenCursors.add(next);

        while (exported < maxEvents) {
            int requested = Math.min(pageSize, maxEvents - exported);
            WarehouseRecoveryAuditPage page = operations.auditPage(tenantId, requested, next);
            for (WarehouseRecoveryOperatorAuditEvent event : page.events()) {
                if (!tenantId.trim().equals(event.tenantId())) {
                    throw new IllegalStateException("Repository returned an audit event outside the requested tenant");
                }
                writeRow(destination, event);
                exported++;
            }
            next = page.nextCursor();
            if (next == null) return new ExportResult(exported, null);
            if (!seenCursors.add(next)) throw new IllegalStateException("Audit pagination cursor repeated");
            if (page.events().isEmpty()) throw new IllegalStateException("Audit pagination returned an empty page with a continuation cursor");
        }
        return new ExportResult(exported, next);
    }

    private static void writeRow(Appendable out, WarehouseRecoveryOperatorAuditEvent event) throws IOException {
        out.append(csv(event.tenantId())).append(',')
                .append(csv(event.commandKey())).append(',')
                .append(csv(event.action())).append(',')
                .append(csv(event.actorId())).append(',')
                .append(csv(event.reason())).append(',')
                .append(Long.toString(event.priorVersion())).append(',')
                .append(Long.toString(event.resultingVersion())).append(',')
                .append(csv(event.correlationId())).append(',')
                .append(csv(event.occurredAt().toString())).append('\n');
    }

    private static String csv(String value) {
        if (value == null) return "";
        String safe = value;
        if (!safe.isEmpty() && (safe.charAt(0) == '=' || safe.charAt(0) == '+'
                || safe.charAt(0) == '-' || safe.charAt(0) == '@'
                || safe.charAt(0) == '\t' || safe.charAt(0) == '\r')) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    public record ExportResult(int exportedCount, String nextCursor) {
        public ExportResult {
            if (exportedCount < 0) throw new IllegalArgumentException("exportedCount must not be negative");
            if (nextCursor != null && nextCursor.isBlank()) throw new IllegalArgumentException("nextCursor must be null or non-blank");
        }
        public boolean complete() { return nextCursor == null; }
    }
}
