package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.domain.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Transport-neutral entry point for structured workspace databases.
 * Tenant and actor identity are always resolved from the trusted host context.
 */
public final class WorkspaceDatabaseApi {
    private final StructuredDatabaseService service;
    private final WorkspaceApi.IdentityProvider identity;

    public WorkspaceDatabaseApi(StructuredDatabaseService service, WorkspaceApi.IdentityProvider identity) {
        this.service = Objects.requireNonNull(service, "service");
        this.identity = Objects.requireNonNull(identity, "identity");
    }

    public WorkspaceDatabase createDatabase(UUID workspaceId, CreateDatabaseRequest request) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(request, "request");
        var actor = currentActor();
        return call(() -> service.createDatabase(actor.tenantId(), workspaceId, actor.actorId(), request.name()));
    }

    public DatabaseTable createTable(UUID databaseId, CreateTableRequest request) {
        Objects.requireNonNull(databaseId, "databaseId");
        Objects.requireNonNull(request, "request");
        var actor = currentActor();
        return call(() -> service.createTable(actor.tenantId(), actor.actorId(), databaseId,
                request.name(), request.properties()));
    }

    public DatabaseTable updateTableSchema(UUID databaseId, UUID tableId, UpdateTableSchemaRequest request) {
        Objects.requireNonNull(databaseId, "databaseId");
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(request, "request");
        var actor = currentActor();
        return call(() -> service.updateTableSchema(actor.tenantId(), actor.actorId(), databaseId, tableId,
                request.expectedSchemaVersion(), request.name(), request.properties()));
    }

    public DatabaseRecord createRecord(UUID databaseId, UUID tableId, RecordValuesRequest request) {
        Objects.requireNonNull(databaseId, "databaseId");
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(request, "request");
        var actor = currentActor();
        return call(() -> service.createRecord(actor.tenantId(), actor.actorId(), databaseId, tableId, request.values()));
    }

    public DatabaseRecord updateRecord(UUID recordId, UpdateRecordRequest request) {
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(request, "request");
        var actor = currentActor();
        return call(() -> service.updateRecord(actor.tenantId(), actor.actorId(), recordId,
                request.expectedRevision(), request.values()));
    }

    public DatabaseRecordPage queryRecords(UUID databaseId, UUID tableId, QueryRecordsRequest request) {
        Objects.requireNonNull(databaseId, "databaseId");
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(request, "request");
        var actor = currentActor();
        return call(() -> service.queryRecords(actor.tenantId(), actor.actorId(), databaseId, tableId,
                request.filters(), request.limit(), request.offset()));
    }

    public DatabaseView createView(UUID databaseId, UUID tableId, CreateViewRequest request) {
        Objects.requireNonNull(databaseId, "databaseId");
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(request, "request");
        var actor = currentActor();
        return call(() -> service.createView(actor.tenantId(), actor.actorId(), databaseId, tableId,
                request.name(), request.type(), request.filters(), request.sorts(),
                request.visibleProperties(), request.groupByProperty()));
    }

    public DatabaseView updateView(UUID databaseId, UUID viewId, UpdateViewRequest request) {
        Objects.requireNonNull(databaseId, "databaseId");
        Objects.requireNonNull(viewId, "viewId");
        Objects.requireNonNull(request, "request");
        var actor = currentActor();
        return call(() -> service.updateView(actor.tenantId(), actor.actorId(), databaseId, viewId,
                request.expectedRevision(), request.name(), request.type(), request.filters(),
                request.sorts(), request.visibleProperties(), request.groupByProperty()));
    }

    public List<DatabaseView> listViews(UUID databaseId, UUID tableId) {
        Objects.requireNonNull(databaseId, "databaseId");
        var actor = currentActor();
        return call(() -> service.findViews(actor.tenantId(), actor.actorId(), databaseId, tableId));
    }

    public DatabaseRecordPage queryView(UUID databaseId, UUID viewId, int limit, int offset) {
        var actor = currentActor();
        return call(() -> service.queryRecordsByView(actor.tenantId(), actor.actorId(), databaseId, viewId, limit, offset));
    }

    public DatabaseRecordBoard queryBoard(UUID databaseId, UUID viewId, int limit, int offset) {
        var actor = currentActor();
        return call(() -> service.queryBoard(actor.tenantId(), actor.actorId(), databaseId, viewId, limit, offset));
    }

    public DatabaseViewValidationResult validateView(UUID databaseId, UUID viewId) {
        var actor = currentActor();
        return call(() -> service.validateView(actor.tenantId(), actor.actorId(), databaseId, viewId));
    }

    public void deleteView(UUID databaseId, UUID viewId) {
        var actor = currentActor();
        call(() -> { service.deleteView(actor.tenantId(), actor.actorId(), databaseId, viewId); return null; });
    }

    private WorkspaceApi.AuthenticatedActor currentActor() {
        WorkspaceApi.AuthenticatedActor actor = identity.currentActor();
        if (actor == null || actor.tenantId() == null || actor.actorId() == null) {
            throw new WorkspaceApi.ApiException(401, "An authenticated tenant and actor are required");
        }
        return actor;
    }

    private static <T> T call(Operation<T> operation) {
        try {
            return operation.run();
        } catch (WorkspaceService.RevisionConflictException conflict) {
            throw new WorkspaceApi.ApiException(409, conflict.getMessage(), conflict);
        } catch (SecurityException denied) {
            throw new WorkspaceApi.ApiException(403, "Access denied", denied);
        } catch (IllegalArgumentException invalid) {
            throw new WorkspaceApi.ApiException(400, invalid.getMessage(), invalid);
        } catch (IllegalStateException conflictOrInvalidState) {
            throw new WorkspaceApi.ApiException(409, conflictOrInvalidState.getMessage(), conflictOrInvalidState);
        }
    }

    @FunctionalInterface
    private interface Operation<T> { T run(); }

    public record CreateDatabaseRequest(String name) {}
    public record CreateTableRequest(String name, List<DatabaseProperty> properties) {
        public CreateTableRequest { properties = properties == null ? List.of() : List.copyOf(properties); }
    }
    public record UpdateTableSchemaRequest(long expectedSchemaVersion, String name, List<DatabaseProperty> properties) {
        public UpdateTableSchemaRequest { properties = properties == null ? List.of() : List.copyOf(properties); }
    }
    public record RecordValuesRequest(Map<String, Object> values) {
        public RecordValuesRequest { values = values == null ? Map.of() : Map.copyOf(values); }
    }
    public record UpdateRecordRequest(long expectedRevision, Map<String, Object> values) {
        public UpdateRecordRequest { values = values == null ? Map.of() : Map.copyOf(values); }
    }
    public record QueryRecordsRequest(Map<String, Object> filters, int limit, int offset) {
        public QueryRecordsRequest { filters = filters == null ? Map.of() : Map.copyOf(filters); }
    }
    public record CreateViewRequest(String name, DatabaseViewType type, Map<String, Object> filters,
                                    List<DatabaseViewSort> sorts, List<String> visibleProperties,
                                    String groupByProperty) {
        public CreateViewRequest {
            filters = filters == null ? Map.of() : Map.copyOf(filters);
            sorts = sorts == null ? List.of() : List.copyOf(sorts);
            visibleProperties = visibleProperties == null ? List.of() : List.copyOf(visibleProperties);
        }
    }
    public record UpdateViewRequest(long expectedRevision, String name, DatabaseViewType type,
                                    Map<String, Object> filters, List<DatabaseViewSort> sorts,
                                    List<String> visibleProperties, String groupByProperty) {
        public UpdateViewRequest {
            filters = filters == null ? Map.of() : Map.copyOf(filters);
            sorts = sorts == null ? List.of() : List.copyOf(sorts);
            visibleProperties = visibleProperties == null ? List.of() : List.copyOf(visibleProperties);
        }
    }
}
