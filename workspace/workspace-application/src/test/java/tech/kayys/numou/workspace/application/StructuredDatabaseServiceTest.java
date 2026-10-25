package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.WorkspacePorts.*;
import tech.kayys.numou.workspace.domain.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class StructuredDatabaseServiceTest {
    private final UUID tenant = UUID.randomUUID();
    private final UUID workspace = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final InMemoryRepository repository = new InMemoryRepository();
    private final StructuredDatabaseService service = new StructuredDatabaseService(
            repository, (t, a, r, id, action) -> {
                if (!tenant.equals(t) || !actor.equals(a)) throw new SecurityException("unauthorized");
            }, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void createsDatabaseTableAndRecordThenUpdatesWithRevision() {
        WorkspaceDatabase database = service.createDatabase(tenant, workspace, actor, "Tasks");
        DatabaseProperty title = new DatabaseProperty(UUID.randomUUID(), "title", "Title",
                DatabasePropertyType.TEXT, true, List.of(), null);
        DatabaseProperty status = new DatabaseProperty(UUID.randomUUID(), "status", "Status",
                DatabasePropertyType.SELECT, true, List.of("todo", "done"), null);
        DatabaseTable table = service.createTable(tenant, actor, database.id(), "Task", List.of(title, status));

        DatabaseRecord first = service.createRecord(tenant, actor, database.id(), table.id(),
                Map.of("title", "Draft", "status", "todo"));
        DatabaseRecord second = service.updateRecord(tenant, actor, first.id(), 1,
                Map.of("title", "Ready", "status", "done"));

        assertEquals(2, second.revision());
        assertEquals("Draft", first.values().get("title"));
        assertEquals("Ready", second.values().get("title"));
        assertEquals(4, repository.audit.size());
        assertEquals(0, repository.lastExpectedRevisionForCreate);
        assertEquals(1, repository.lastExpectedRevisionForUpdate);
    }

    @Test
    void rejectsStaleRevisionAndMissingRequiredValues() {
        WorkspaceDatabase database = service.createDatabase(tenant, workspace, actor, "Tasks");
        DatabaseTable table = service.createTable(tenant, actor, database.id(), "Task", List.of(
                new DatabaseProperty(UUID.randomUUID(), "title", "Title", DatabasePropertyType.TEXT, true, List.of(), null)));
        DatabaseRecord record = service.createRecord(tenant, actor, database.id(), table.id(), Map.of("title", "Draft"));
        assertThrows(IllegalArgumentException.class, () -> service.updateRecord(tenant, actor, record.id(), 0, Map.of("title", "x")));
        assertThrows(IllegalArgumentException.class, () -> service.createRecord(tenant, actor, database.id(), table.id(), Map.of()));
    }

    @Test
    void queriesRecordsWithValidatedExactFiltersAndBoundedPagination() {
        WorkspaceDatabase database = service.createDatabase(tenant, workspace, actor, "Query");
        DatabaseTable table = service.createTable(tenant, actor, database.id(), "Tasks", List.of(
                new DatabaseProperty(UUID.randomUUID(), "title", "Title", DatabasePropertyType.TEXT, true, List.of(), null),
                new DatabaseProperty(UUID.randomUUID(), "status", "Status", DatabasePropertyType.SELECT, true, List.of("todo", "done"), null)));
        service.createRecord(tenant, actor, database.id(), table.id(), Map.of("title", "A", "status", "todo"));
        service.createRecord(tenant, actor, database.id(), table.id(), Map.of("title", "B", "status", "done"));
        service.createRecord(tenant, actor, database.id(), table.id(), Map.of("title", "C", "status", "todo"));

        DatabaseRecordPage page = service.queryRecords(tenant, actor, database.id(), table.id(),
                Map.of("status", "todo"), 1, 1);
        assertEquals(2, page.total());
        assertEquals(1, page.records().size());
        assertEquals(1, page.limit());
        assertEquals(1, page.offset());
        assertThrows(IllegalArgumentException.class, () -> service.queryRecords(
                tenant, actor, database.id(), table.id(), Map.of("unknown", "x"), 10, 0));
        assertThrows(IllegalArgumentException.class, () -> service.queryRecords(
                tenant, actor, database.id(), table.id(), Map.of(), 201, 0));
    }

    @Test
    void createsAndListsValidatedPersonalSavedViews() {
        WorkspaceDatabase database = service.createDatabase(tenant, workspace, actor, "Saved views");
        DatabaseTable table = service.createTable(tenant, actor, database.id(), "Tasks", List.of(
                new DatabaseProperty(UUID.randomUUID(), "title", "Title", DatabasePropertyType.TEXT, true, List.of(), null),
                new DatabaseProperty(UUID.randomUUID(), "status", "Status", DatabasePropertyType.SELECT, true, List.of("todo", "done"), null)));
        DatabaseView view = service.createView(tenant, actor, database.id(), table.id(), "Open tasks",
                DatabaseViewType.TABLE, Map.of("status", "todo"),
                List.of(new DatabaseViewSort("title", DatabaseViewSort.Direction.ASC)), List.of("title", "status"));
        assertEquals("Open tasks", view.name());
        assertEquals("todo", view.filters().get("status"));
        assertEquals(1, service.findViews(tenant, actor, database.id(), table.id()).size());
        assertThrows(IllegalArgumentException.class, () -> service.createView(tenant, actor, database.id(),
                table.id(), "Bad view", DatabaseViewType.TABLE, Map.of("unknown", "x"), List.of(), List.of()));
    }

    @Test
    void executesPersonalSavedViewWithFiltersAndTypedSorts() {
        WorkspaceDatabase database = service.createDatabase(tenant, workspace, actor, "Executable views");
        DatabaseTable table = service.createTable(tenant, actor, database.id(), "Tasks", List.of(
                new DatabaseProperty(UUID.randomUUID(), "title", "Title", DatabasePropertyType.TEXT, true, List.of(), null),
                new DatabaseProperty(UUID.randomUUID(), "status", "Status", DatabasePropertyType.SELECT, true, List.of("todo", "done"), null)));
        service.createRecord(tenant, actor, database.id(), table.id(), Map.of("title", "A", "status", "todo"));
        DatabaseView view = service.createView(tenant, actor, database.id(), table.id(), "Todo by title",
                DatabaseViewType.TABLE, Map.of("status", "todo"),
                List.of(new DatabaseViewSort("title", DatabaseViewSort.Direction.ASC)), List.of("title"));
        DatabaseRecordPage page = service.queryRecordsByView(tenant, actor, database.id(), view.id(), 20, 0);
        assertEquals(1, page.total());
        assertEquals("todo", page.records().get(0).values().get("status"));
        assertEquals(List.of(new DatabaseViewSort("title", DatabaseViewSort.Direction.ASC)), repository.lastSorts);
        assertThrows(IllegalArgumentException.class,
                () -> service.queryRecordsByView(tenant, UUID.randomUUID(), database.id(), view.id(), 20, 0));
    }

    @Test
    void groupsBoardViewBySelectPropertyAndRejectsNonBoardOrInvalidGrouping() {
        WorkspaceDatabase database = service.createDatabase(tenant, workspace, actor, "Boards");
        DatabaseTable table = service.createTable(tenant, actor, database.id(), "Tasks", List.of(
                new DatabaseProperty(UUID.randomUUID(), "title", "Title", DatabasePropertyType.TEXT, true, List.of(), null),
                new DatabaseProperty(UUID.randomUUID(), "status", "Status", DatabasePropertyType.SELECT, false, List.of("todo", "doing", "done"), null)));
        service.createRecord(tenant, actor, database.id(), table.id(), Map.of("title", "One", "status", "todo"));
        service.createRecord(tenant, actor, database.id(), table.id(), Map.of("title", "Two", "status", "done"));
        service.createRecord(tenant, actor, database.id(), table.id(), Map.of("title", "Three"));
        DatabaseView board = service.createView(tenant, actor, database.id(), table.id(), "Task board",
                DatabaseViewType.BOARD, Map.of(), List.of(), List.of("title", "status"));
        DatabaseRecordBoard result = service.queryBoard(tenant, actor, database.id(), board.id(), "status", 20, 0);
        assertEquals(3, result.totalRecords());
        assertEquals("status", result.groupByProperty());
        assertEquals(4, result.groups().size());
        assertEquals(1, result.groups().get(0).records().size());
        assertEquals("todo", result.groups().get(0).value());
        assertEquals(1, result.groups().get(2).records().size());
        assertEquals("done", result.groups().get(2).value());
        assertEquals(1, result.groups().get(3).records().size());
        assertNull(result.groups().get(3).value());
        DatabaseView tableView = service.createView(tenant, actor, database.id(), table.id(), "Plain table",
                DatabaseViewType.TABLE, Map.of(), List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> service.queryBoard(
                tenant, actor, database.id(), tableView.id(), "status", 20, 0));
        assertThrows(IllegalArgumentException.class, () -> service.queryBoard(
                tenant, actor, database.id(), board.id(), "title", 20, 0));
    }

    @Test
    void relationValuesMustResolveToReadableRecordsInTheConfiguredTable() {
        WorkspaceDatabase database = service.createDatabase(tenant, workspace, actor, "Relations");
        DatabaseTable targetTable = service.createTable(tenant, actor, database.id(), "Targets", List.of(
                new DatabaseProperty(UUID.randomUUID(), "title", "Title",
                        DatabasePropertyType.TEXT, true, List.of(), null)));
        DatabaseTable otherTable = service.createTable(tenant, actor, database.id(), "Other", List.of(
                new DatabaseProperty(UUID.randomUUID(), "title", "Title",
                        DatabasePropertyType.TEXT, true, List.of(), null)));
        DatabaseTable sourceTable = service.createTable(tenant, actor, database.id(), "Sources", List.of(
                new DatabaseProperty(UUID.randomUUID(), "targets", "Targets",
                        DatabasePropertyType.RELATION, false, List.of(), targetTable.id())));

        DatabaseRecord target = service.createRecord(tenant, actor, database.id(), targetTable.id(),
                Map.of("title", "Allowed target"));
        DatabaseRecord other = service.createRecord(tenant, actor, database.id(), otherTable.id(),
                Map.of("title", "Wrong table"));

        DatabaseRecord linked = service.createRecord(tenant, actor, database.id(), sourceTable.id(),
                Map.of("targets", List.of(target.id())));
        assertEquals(List.of(target.id()), linked.values().get("targets"));

        assertThrows(IllegalArgumentException.class,
                () -> service.createRecord(tenant, actor, database.id(), sourceTable.id(),
                        Map.of("targets", List.of(other.id()))));
        assertThrows(IllegalArgumentException.class,
                () -> service.createRecord(tenant, actor, database.id(), sourceTable.id(),
                        Map.of("targets", List.of(UUID.randomUUID()))));
    }

    @Test
    void relationValuesRequireReadPermissionOnEveryTarget() {
        WorkspaceDatabase database = service.createDatabase(tenant, workspace, actor, "Protected relations");
        DatabaseTable targetTable = service.createTable(tenant, actor, database.id(), "Targets", List.of(
                new DatabaseProperty(UUID.randomUUID(), "title", "Title",
                        DatabasePropertyType.TEXT, true, List.of(), null)));
        DatabaseTable sourceTable = service.createTable(tenant, actor, database.id(), "Sources", List.of(
                new DatabaseProperty(UUID.randomUUID(), "targets", "Targets",
                        DatabasePropertyType.RELATION, false, List.of(), targetTable.id())));
        DatabaseRecord target = service.createRecord(tenant, actor, database.id(), targetTable.id(),
                Map.of("title", "Private target"));
        StructuredDatabaseService restricted = new StructuredDatabaseService(repository,
                (t, a, resource, id, action) -> {
                    if (resource == Resource.DATABASE_RECORD && action == Action.READ) {
                        throw new SecurityException("record read denied");
                    }
                }, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        assertThrows(SecurityException.class,
                () -> restricted.createRecord(tenant, actor, database.id(), sourceTable.id(),
                        Map.of("targets", List.of(target.id()))));
    }

    @Test
    void boardProjectionKeepsPageBoundedAndReportsFilteredTotal() {
        WorkspaceDatabase database = service.createDatabase(tenant, workspace, actor, "Paged board");
        DatabaseTable table = service.createTable(tenant, actor, database.id(), "Tasks", List.of(
                new DatabaseProperty(UUID.randomUUID(), "title", "Title", DatabasePropertyType.TEXT, true, List.of(), null),
                new DatabaseProperty(UUID.randomUUID(), "status", "Status", DatabasePropertyType.SELECT, false,
                        List.of("todo", "doing", "done"), null)));
        service.createRecord(tenant, actor, database.id(), table.id(), Map.of("title", "One", "status", "todo"));
        service.createRecord(tenant, actor, database.id(), table.id(), Map.of("title", "Two", "status", "doing"));
        service.createRecord(tenant, actor, database.id(), table.id(), Map.of("title", "Three", "status", "done"));
        DatabaseView board = service.createView(tenant, actor, database.id(), table.id(), "Paged board",
                DatabaseViewType.BOARD, Map.of(), List.of(), List.of("title", "status"));

        DatabaseRecordBoard firstPage = service.queryBoard(tenant, actor, database.id(), board.id(), "status", 1, 0);
        assertEquals(3, firstPage.totalRecords());
        assertEquals(1, firstPage.limit());
        assertEquals(0, firstPage.offset());
        assertEquals(1, firstPage.groups().stream().mapToInt(group -> group.records().size()).sum());

        DatabaseRecordBoard lastPage = service.queryBoard(tenant, actor, database.id(), board.id(), "status", 1, 2);
        assertEquals(3, lastPage.totalRecords());
        assertEquals(2, lastPage.offset());
        assertEquals(1, lastPage.groups().stream().mapToInt(group -> group.records().size()).sum());
        assertThrows(IllegalArgumentException.class,
                () -> service.queryBoard(tenant, actor, database.id(), board.id(), "status", 201, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.queryBoard(tenant, actor, database.id(), board.id(), "status", 20, -1));
    }

    private static final class InMemoryRepository implements StructuredDatabaseRepository {
        private List<DatabaseViewSort> lastSorts = List.of();
        private final Map<UUID, WorkspaceDatabase> databases = new HashMap<>();
        private final Map<UUID, DatabaseTable> tables = new HashMap<>();
        private final Map<UUID, DatabaseRecord> records = new HashMap<>();
        private final Map<UUID, DatabaseView> views = new HashMap<>();
        private final List<AuditEntry> audit = new ArrayList<>();
        private long lastExpectedRevisionForCreate = -1;
        private long lastExpectedRevisionForUpdate = -1;

        @Override public boolean saveDatabase(WorkspaceDatabase database, AuditEntry entry) {
            if (databases.putIfAbsent(database.id(), database) != null) return false;
            audit.add(entry); return true;
        }
        @Override public Optional<WorkspaceDatabase> findDatabase(UUID tenantId, UUID databaseId) {
            return Optional.ofNullable(databases.get(databaseId)).filter(db -> db.tenantId().equals(tenantId));
        }
        @Override public boolean saveTable(DatabaseTable table, AuditEntry entry) {
            if (tables.putIfAbsent(table.id(), table) != null) return false;
            audit.add(entry); return true;
        }
        @Override public Optional<DatabaseTable> findTable(UUID tenantId, UUID databaseId, UUID tableId) {
            return Optional.ofNullable(tables.get(tableId))
                    .filter(t -> t.tenantId().equals(tenantId) && t.databaseId().equals(databaseId));
        }
        @Override public Optional<DatabaseRecord> findRecord(UUID tenantId, UUID recordId) {
            return Optional.ofNullable(records.get(recordId)).filter(r -> r.tenantId().equals(tenantId));
        }
        @Override public DatabaseRecordPage queryRecords(UUID tenantId, UUID databaseId, UUID tableId,
                                                          Map<String, Object> filters, int limit, int offset) {
            List<DatabaseRecord> matched = records.values().stream()
                    .filter(r -> r.tenantId().equals(tenantId) && r.databaseId().equals(databaseId)
                            && r.tableId().equals(tableId))
                    .filter(r -> filters.entrySet().stream()
                            .allMatch(e -> Objects.equals(r.values().get(e.getKey()), e.getValue())))
                    .sorted(Comparator.comparing(DatabaseRecord::createdAt).thenComparing(DatabaseRecord::id))
                    .toList();
            int from = Math.min(offset, matched.size());
            int to = Math.min(from + limit, matched.size());
            return new DatabaseRecordPage(matched.subList(from, to), matched.size(), limit, offset);
        }
        @Override public DatabaseRecordPage queryRecords(UUID tenantId, UUID databaseId, UUID tableId,
                Map<String, Object> filters, List<DatabaseViewSort> sorts, int limit, int offset) {
            lastSorts = List.copyOf(sorts);
            return queryRecords(tenantId, databaseId, tableId, filters, limit, offset);
        }
        @Override public boolean saveView(DatabaseView view, AuditEntry entry) {
            if (views.values().stream().anyMatch(existing -> existing.tenantId().equals(view.tenantId())
                    && existing.tableId().equals(view.tableId()) && existing.createdBy().equals(view.createdBy())
                    && existing.name().equals(view.name()))) return false;
            views.put(view.id(), view); audit.add(entry); return true;
        }
        @Override public Optional<DatabaseView> findView(UUID tenantId, UUID databaseId, UUID viewId) {
            return Optional.ofNullable(views.get(viewId)).filter(v -> v.tenantId().equals(tenantId)
                    && v.databaseId().equals(databaseId));
        }
        @Override public List<DatabaseView> findViews(UUID tenantId, UUID databaseId, UUID tableId, UUID actorId) {
            return views.values().stream().filter(v -> v.tenantId().equals(tenantId)
                    && v.databaseId().equals(databaseId) && v.tableId().equals(tableId)
                    && v.createdBy().equals(actorId)).sorted(Comparator.comparing(DatabaseView::name)).toList();
        }
        @Override public boolean saveRecord(DatabaseRecord record, long expectedRevision, AuditEntry entry) {
            DatabaseRecord current = records.get(record.id());
            if (expectedRevision == 0) {
                lastExpectedRevisionForCreate = expectedRevision;
                if (current != null || record.revision() != 1) return false;
            } else {
                lastExpectedRevisionForUpdate = expectedRevision;
                if (current == null || current.revision() != expectedRevision || record.revision() != expectedRevision + 1) return false;
            }
            records.put(record.id(), record); audit.add(entry); return true;
        }
    }
}
