package tech.kayys.numou.workspace.adapter.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.numou.workspace.application.WorkspaceDatabaseApi;
import tech.kayys.numou.workspace.domain.DatabaseProperty;
import tech.kayys.numou.workspace.domain.DatabasePropertyType;
import tech.kayys.numou.workspace.domain.DatabaseViewSort;
import tech.kayys.numou.workspace.domain.DatabaseViewType;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Optional REST transport for the reusable structured workspace database capability. */
@Path("/api/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WorkspaceDatabaseResource {
    private final WorkspaceDatabaseApi databases;

    @Inject
    public WorkspaceDatabaseResource(WorkspaceDatabaseApi databases) {
        this.databases = databases;
    }

    @POST
    @Path("/workspaces/{workspaceId}/databases")
    public Response createDatabase(@PathParam("workspaceId") UUID workspaceId,
                                   @Valid CreateDatabaseRequest request) {
        var created = databases.createDatabase(workspaceId,
                new WorkspaceDatabaseApi.CreateDatabaseRequest(request.name()));
        return Response.created(URI.create("/api/v1/databases/" + created.id())).entity(created).build();
    }

    @POST
    @Path("/databases/{databaseId}/tables")
    public Response createTable(@PathParam("databaseId") UUID databaseId, @Valid CreateTableRequest request) {
        var created = databases.createTable(databaseId, new WorkspaceDatabaseApi.CreateTableRequest(
                request.name(), properties(request.properties())));
        return Response.created(URI.create("/api/v1/databases/" + databaseId + "/tables/" + created.id()))
                .entity(created).build();
    }

    @PUT
    @Path("/databases/{databaseId}/tables/{tableId}/schema")
    public Object updateSchema(@PathParam("databaseId") UUID databaseId, @PathParam("tableId") UUID tableId,
                               @Valid UpdateTableSchemaRequest request) {
        return databases.updateTableSchema(databaseId, tableId, new WorkspaceDatabaseApi.UpdateTableSchemaRequest(
                request.expectedSchemaVersion(), request.name(), properties(request.properties())));
    }

    @POST
    @Path("/databases/{databaseId}/tables/{tableId}/records")
    public Response createRecord(@PathParam("databaseId") UUID databaseId, @PathParam("tableId") UUID tableId,
                                 RecordValuesRequest request) {
        var created = databases.createRecord(databaseId, tableId,
                new WorkspaceDatabaseApi.RecordValuesRequest(request == null ? Map.of() : request.values()));
        return Response.created(URI.create("/api/v1/records/" + created.id())).entity(created).build();
    }

    @PUT
    @Path("/records/{recordId}")
    public Object updateRecord(@PathParam("recordId") UUID recordId, @Valid UpdateRecordRequest request) {
        return databases.updateRecord(recordId,
                new WorkspaceDatabaseApi.UpdateRecordRequest(request.expectedRevision(), request.values()));
    }

    @GET
    @Path("/databases/{databaseId}/tables/{tableId}/records")
    public Object queryRecords(@PathParam("databaseId") UUID databaseId, @PathParam("tableId") UUID tableId,
                               @QueryParam("limit") @DefaultValue("50") @Min(1) @Max(200) int limit,
                               @QueryParam("offset") @DefaultValue("0") @Min(0) int offset) {
        return databases.queryRecords(databaseId, tableId,
                new WorkspaceDatabaseApi.QueryRecordsRequest(Map.of(), limit, offset));
    }

    @POST
    @Path("/databases/{databaseId}/tables/{tableId}/records/query")
    public Object filterRecords(@PathParam("databaseId") UUID databaseId, @PathParam("tableId") UUID tableId,
                                @Valid QueryRecordsRequest request) {
        return databases.queryRecords(databaseId, tableId,
                new WorkspaceDatabaseApi.QueryRecordsRequest(request.filters(), request.limit(), request.offset()));
    }

    @POST
    @Path("/databases/{databaseId}/tables/{tableId}/views")
    public Response createView(@PathParam("databaseId") UUID databaseId, @PathParam("tableId") UUID tableId,
                               @Valid ViewRequest request) {
        var created = databases.createView(databaseId, tableId, new WorkspaceDatabaseApi.CreateViewRequest(
                request.name(), request.type(), request.filters(), sorts(request.sorts()),
                request.visibleProperties(), request.groupByProperty()));
        return Response.created(URI.create("/api/v1/databases/" + databaseId + "/views/" + created.id()))
                .entity(created).build();
    }

    @GET
    @Path("/databases/{databaseId}/tables/{tableId}/views")
    public Object listViews(@PathParam("databaseId") UUID databaseId, @PathParam("tableId") UUID tableId) {
        return databases.listViews(databaseId, tableId);
    }

    @PUT
    @Path("/databases/{databaseId}/views/{viewId}")
    public Object updateView(@PathParam("databaseId") UUID databaseId, @PathParam("viewId") UUID viewId,
                             @Valid UpdateViewRequest request) {
        return databases.updateView(databaseId, viewId, new WorkspaceDatabaseApi.UpdateViewRequest(
                request.expectedRevision(), request.name(), request.type(), request.filters(),
                sorts(request.sorts()), request.visibleProperties(), request.groupByProperty()));
    }

    @GET
    @Path("/databases/{databaseId}/views/{viewId}/records")
    public Object queryView(@PathParam("databaseId") UUID databaseId, @PathParam("viewId") UUID viewId,
                            @QueryParam("limit") @DefaultValue("50") @Min(1) @Max(200) int limit,
                            @QueryParam("offset") @DefaultValue("0") @Min(0) int offset) {
        return databases.queryView(databaseId, viewId, limit, offset);
    }

    @GET
    @Path("/databases/{databaseId}/views/{viewId}/board")
    public Object queryBoard(@PathParam("databaseId") UUID databaseId, @PathParam("viewId") UUID viewId,
                             @QueryParam("limit") @DefaultValue("100") @Min(1) @Max(200) int limit,
                             @QueryParam("offset") @DefaultValue("0") @Min(0) int offset) {
        return databases.queryBoard(databaseId, viewId, limit, offset);
    }

    @GET
    @Path("/databases/{databaseId}/views/{viewId}/validation")
    public Object validateView(@PathParam("databaseId") UUID databaseId, @PathParam("viewId") UUID viewId) {
        return databases.validateView(databaseId, viewId);
    }

    @DELETE
    @Path("/databases/{databaseId}/views/{viewId}")
    public Response deleteView(@PathParam("databaseId") UUID databaseId, @PathParam("viewId") UUID viewId) {
        databases.deleteView(databaseId, viewId);
        return Response.noContent().build();
    }

    private static List<DatabaseProperty> properties(List<PropertyRequest> properties) {
        if (properties == null) return List.of();
        return properties.stream().map(p -> new DatabaseProperty(p.id() == null ? UUID.randomUUID() : p.id(),
                p.key(), p.label(), p.type(), p.required(), p.options(), p.relatedTableId())).toList();
    }

    private static List<DatabaseViewSort> sorts(List<DatabaseViewSort> sorts) {
        return sorts == null ? List.of() : List.copyOf(sorts);
    }

    public record CreateDatabaseRequest(@NotBlank String name) {}
    public record CreateTableRequest(@NotBlank String name, List<@Valid PropertyRequest> properties) {}
    public record UpdateTableSchemaRequest(@Min(1) long expectedSchemaVersion, String name,
                                           List<@Valid PropertyRequest> properties) {}
    public record PropertyRequest(UUID id, @NotBlank String key, @NotBlank String label,
                                  @NotNull DatabasePropertyType type, boolean required,
                                  List<String> options, UUID relatedTableId) {}
    public record RecordValuesRequest(Map<String, Object> values) {}
    public record UpdateRecordRequest(@Min(1) long expectedRevision, @NotNull Map<String, Object> values) {}
    public record QueryRecordsRequest(Map<String, Object> filters,
                                      @Min(1) @Max(200) int limit, @Min(0) int offset) {
        public QueryRecordsRequest { filters = filters == null ? Map.of() : filters; }
    }
    public record ViewRequest(@NotBlank String name, @NotNull DatabaseViewType type, Map<String, Object> filters,
                              List<DatabaseViewSort> sorts, List<String> visibleProperties,
                              String groupByProperty) {
        public ViewRequest {
            filters = filters == null ? Map.of() : filters;
            sorts = sorts == null ? List.of() : sorts;
            visibleProperties = visibleProperties == null ? List.of() : visibleProperties;
        }
    }
    public record UpdateViewRequest(@Min(1) long expectedRevision, @NotBlank String name,
                                    @NotNull DatabaseViewType type, Map<String, Object> filters,
                                    List<DatabaseViewSort> sorts, List<String> visibleProperties,
                                    String groupByProperty) {
        public UpdateViewRequest {
            filters = filters == null ? Map.of() : filters;
            sorts = sorts == null ? List.of() : sorts;
            visibleProperties = visibleProperties == null ? List.of() : visibleProperties;
        }
    }
}
