package tech.kayys.numou.asset.interfaces.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.asset.application.command.*;
import tech.kayys.numou.asset.application.query.*;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.relationship.AssetRelationshipId;
import tech.kayys.numou.asset.domain.relationship.AssetRelationshipType;
import tech.kayys.numou.asset.domain.valueobject.AssetStatus;
import tech.kayys.numou.asset.domain.valueobject.AssetType;
import tech.kayys.numou.foundation.adapter.context.TenantContext;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Inbound REST adapter for the Asset bounded context (ASSET-10).
 *
 * <p>Transport concerns stay here: every use case is delegated to an
 * application handler and every response is tenant-scoped. The tenant comes
 * from the {@code X-Tenant-Id} header, falling back to the reactive
 * {@link TenantContext}.</p>
 */
@Path("/api/v1/assets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Asset API", description = "Asset management endpoints")
public class AssetResource {

    private final CreateAssetHandler createAssetHandler;
    private final UpdateAssetHandler updateAssetHandler;
    private final ActivateAssetHandler activateAssetHandler;
    private final SuspendAssetHandler suspendAssetHandler;
    private final RetireAssetHandler retireAssetHandler;
    private final DisposeAssetHandler disposeAssetHandler;
    private final ChangeAssetLocationHandler changeAssetLocationHandler;
    private final ClearAssetLocationHandler clearAssetLocationHandler;
    private final AssignAssetHandler assignAssetHandler;
    private final UnassignAssetHandler unassignAssetHandler;
    private final ClassifyAssetHandler classifyAssetHandler;
    private final ClearAssetClassificationHandler clearAssetClassificationHandler;
    private final AddAssetRelationshipHandler addAssetRelationshipHandler;
    private final RemoveAssetRelationshipHandler removeAssetRelationshipHandler;
    private final GetAssetHandler getAssetHandler;
    private final SearchAssetsHandler searchAssetsHandler;
    private final GetAssetMovementsHandler getAssetMovementsHandler;
    private final ReplaceAssetMetadataHandler replaceAssetMetadataHandler;
    @jakarta.inject.Inject
    tech.kayys.numou.asset.application.query.GetAssetHierarchyHandler hierarchyHandler;

    @Inject
    public AssetResource(
            CreateAssetHandler createAssetHandler,
            UpdateAssetHandler updateAssetHandler,
            ActivateAssetHandler activateAssetHandler,
            SuspendAssetHandler suspendAssetHandler,
            RetireAssetHandler retireAssetHandler,
            DisposeAssetHandler disposeAssetHandler,
            ChangeAssetLocationHandler changeAssetLocationHandler,
            ClearAssetLocationHandler clearAssetLocationHandler,
            AssignAssetHandler assignAssetHandler,
            UnassignAssetHandler unassignAssetHandler,
            ClassifyAssetHandler classifyAssetHandler,
            ClearAssetClassificationHandler clearAssetClassificationHandler,
            AddAssetRelationshipHandler addAssetRelationshipHandler,
            RemoveAssetRelationshipHandler removeAssetRelationshipHandler,
            GetAssetHandler getAssetHandler,
            SearchAssetsHandler searchAssetsHandler,
            GetAssetMovementsHandler getAssetMovementsHandler,
            ReplaceAssetMetadataHandler replaceAssetMetadataHandler
    ) {
        this.createAssetHandler = createAssetHandler;
        this.updateAssetHandler = updateAssetHandler;
        this.activateAssetHandler = activateAssetHandler;
        this.suspendAssetHandler = suspendAssetHandler;
        this.retireAssetHandler = retireAssetHandler;
        this.disposeAssetHandler = disposeAssetHandler;
        this.changeAssetLocationHandler = changeAssetLocationHandler;
        this.clearAssetLocationHandler = clearAssetLocationHandler;
        this.assignAssetHandler = assignAssetHandler;
        this.unassignAssetHandler = unassignAssetHandler;
        this.classifyAssetHandler = classifyAssetHandler;
        this.clearAssetClassificationHandler = clearAssetClassificationHandler;
        this.addAssetRelationshipHandler = addAssetRelationshipHandler;
        this.removeAssetRelationshipHandler = removeAssetRelationshipHandler;
        this.getAssetHandler = getAssetHandler;
        this.searchAssetsHandler = searchAssetsHandler;
        this.getAssetMovementsHandler = getAssetMovementsHandler;
        this.replaceAssetMetadataHandler = replaceAssetMetadataHandler;
    }

    private static String tenant(String header) {
        if (header != null && !header.isBlank()) {
            TenantContext.setTenantId(header);
            return header;
        }
        String current = TenantContext.getTenantId();
        if (current == null || current.isBlank()) {
            throw new BusinessRuleViolation("tenant is required (X-Tenant-Id header)");
        }
        return current;
    }

    @POST
    @Operation(summary = "Create an asset")
    public Uni<Response> create(
            @HeaderParam("X-Tenant-Id") String header,
            CreateAssetRequest request
    ) {
        String tenantId = tenant(header);
        return createAssetHandler
                .handle(new CreateAssetCommand(tenantId, request.assetNumber(), request.name(), request.type()))
                .map(result -> {
                    CreateAssetResult created = result.orElseThrow();
                    return Response
                            .created(URI.create("/api/v1/assets/" + created.assetId()))
                            .entity(created)
                            .build();
                })
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get an asset by id")
    public Uni<Response> get(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id
    ) {
        String tenantId = tenant(header);
        return getAssetHandler
                .handle(new GetAssetQuery(tenantId, AssetId.of(id)))
                .map(result -> Response.ok(result.orElseThrow()).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @GET
    @Operation(summary = "Search assets")
    public Uni<Response> search(
            @HeaderParam("X-Tenant-Id") String header,
            @QueryParam("status") AssetStatus status,
            @QueryParam("type") AssetType type,
            @QueryParam("locationId") String locationId,
            @QueryParam("partyId") String partyId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size
    ) {
        String tenantId = tenant(header);
        AssetSearchCriteria criteria =
                new AssetSearchCriteria(tenantId, status, type, locationId, partyId, page, size);
        return searchAssetsHandler
                .handle(criteria)
                .map((AssetPage<AssetView> assetPage) -> Response.ok(assetPage).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @POST
    @Path("/{id}/activate")
    @Operation(summary = "Activate a draft asset")
    public Uni<Response> activate(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id
    ) {
        String tenantId = tenant(header);
        return activateAssetHandler
                .handle(new ActivateAssetCommand(tenantId, AssetId.of(id)))
                .map(result -> Response.ok(result.orElseThrow()).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @POST
    @Path("/{id}/suspend")
    @Operation(summary = "Suspend an active asset")
    public Uni<Response> suspend(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id
    ) {
        String tenantId = tenant(header);
        return suspendAssetHandler
                .handle(new SuspendAssetCommand(tenantId, AssetId.of(id)))
                .map(result -> Response.ok(result.orElseThrow()).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @POST
    @Path("/{id}/retire")
    @Operation(summary = "Retire an asset")
    public Uni<Response> retire(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id
    ) {
        String tenantId = tenant(header);
        return retireAssetHandler
                .handle(new RetireAssetCommand(tenantId, AssetId.of(id)))
                .map(result -> Response.ok(result.orElseThrow()).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @POST
    @Path("/{id}/dispose")
    @Operation(summary = "Dispose a retired asset")
    public Uni<Response> dispose(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id
    ) {
        String tenantId = tenant(header);
        return disposeAssetHandler
                .handle(new DisposeAssetCommand(tenantId, AssetId.of(id)))
                .map(result -> Response.ok(result.orElseThrow()).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update asset name and/or type")
    public Uni<Response> update(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id,
            UpdateAssetRequest request
    ) {
        String tenantId = tenant(header);
        return updateAssetHandler
                .handle(new UpdateAssetCommand(tenantId, AssetId.of(id), request.name(), request.type()))
                .map(result -> Response.ok(result.orElseThrow()).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @PUT
    @Path("/{id}/location")
    @Operation(summary = "Change the current location of an asset")
    public Uni<Response> changeLocation(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id,
            LocationRequest request
    ) {
        String tenantId = tenant(header);
        return changeAssetLocationHandler
                .handle(new ChangeAssetLocationCommand(tenantId, AssetId.of(id),
                        request.locationId(), request.locationName()))
                .map(result -> Response.ok(result.orElseThrow()).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @DELETE
    @Path("/{id}/location")
    @Operation(summary = "Clear the current location of an asset")
    public Uni<Response> clearLocation(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id
    ) {
        String tenantId = tenant(header);
        return clearAssetLocationHandler
                .handle(new ClearAssetLocationCommand(tenantId, AssetId.of(id)))
                .map(result -> Response.ok(result.orElseThrow()).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @PUT
    @Path("/{id}/assignment")
    @Operation(summary = "Assign custody of an asset")
    public Uni<Response> assign(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id,
            CustodyRequest request
    ) {
        String tenantId = tenant(header);
        return assignAssetHandler
                .handle(new AssignAssetCommand(tenantId, AssetId.of(id),
                        request.partyId(), request.partyType(), request.partyName()))
                .map(result -> Response.ok(result.orElseThrow()).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @DELETE
    @Path("/{id}/assignment")
    @Operation(summary = "Unassign custody of an asset")
    public Uni<Response> unassign(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id
    ) {
        String tenantId = tenant(header);
        return unassignAssetHandler
                .handle(new UnassignAssetCommand(tenantId, AssetId.of(id)))
                .map(result -> Response.ok(result.orElseThrow()).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @PUT
    @Path("/{id}/classification")
    @Operation(summary = "Classify an asset")
    public Uni<Response> classify(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id,
            ClassificationRequest request
    ) {
        String tenantId = tenant(header);
        return classifyAssetHandler
                .handle(new ClassifyAssetCommand(tenantId, AssetId.of(id),
                        request.classificationId(), request.classificationName()))
                .map(result -> Response.ok(result.orElseThrow()).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @DELETE
    @Path("/{id}/classification")
    @Operation(summary = "Clear the classification of an asset")
    public Uni<Response> clearClassification(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id
    ) {
        String tenantId = tenant(header);
        return clearAssetClassificationHandler
                .handle(new ClearAssetClassificationCommand(tenantId, AssetId.of(id)))
                .map(result -> Response.ok(result.orElseThrow()).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @POST
    @Path("/{id}/relationships")
    @Operation(summary = "Create a relationship to another asset")
    public Uni<Response> addRelationship(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id,
            RelationshipRequest request
    ) {
        String tenantId = tenant(header);
        return addAssetRelationshipHandler
                .handle(new AddAssetRelationshipCommand(tenantId, AssetId.of(id),
                        AssetId.of(request.relatedAssetId()), request.type()))
                .map(result -> Response.created(URI.create(
                                "/api/v1/assets/" + id + "/relationships"))
                        .entity(result.orElseThrow())
                        .build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @DELETE
    @Path("/relationships/{relationshipId}")
    @Operation(summary = "Remove a relationship")
    public Uni<Response> removeRelationship(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("relationshipId") UUID relationshipId
    ) {
        String tenantId = tenant(header);
        return removeAssetRelationshipHandler
                .handle(new RemoveAssetRelationshipCommand(tenantId,
                        AssetRelationshipId.of(relationshipId)))
                .map(result -> Response.ok(result.orElseThrow()).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @GET
    @Path("/{id}/movements")
    @Operation(summary = "List the movement history of an asset (ASSET-13)")
    public Uni<Response> movements(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id
    ) {
        String tenantId = tenant(header);
        return getAssetMovementsHandler
                .handle(new GetAssetMovementsQuery(tenantId, AssetId.of(id)))
                .map(items -> Response.ok(
                        items.stream().map(MovementResponse::from).toList()).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @PUT
    @Path("/{id}/attributes")
    @Operation(summary = "Replace the dynamic attributes of an asset (ASSET-15)")
    public Uni<Response> replaceAttributes(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id,
            AttributesRequest request
    ) {
        String tenantId = tenant(header);
        java.util.List<tech.kayys.numou.asset.domain.classification.AssetAttribute> attributes =
                request.attributes() == null ? java.util.List.of() : request.attributes().stream()
                        .map(a -> new tech.kayys.numou.asset.domain.classification.AssetAttribute(
                                a.key(), a.value(), a.type()))
                        .toList();
        return replaceAssetMetadataHandler
                .handle(new ReplaceAssetMetadataCommand(tenantId, AssetId.of(id), attributes))
                .map(result -> Response.ok(result.orElseThrow().value()).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    // ── ASSET-17 hierarchy read model (appended; existing API untouched) ──

    @GET
    @Path("/{id}/parent")
    @Operation(summary = "Get the direct hierarchical parent of an asset (ASSET-17)")
    public Uni<Response> parent(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id
    ) {
        String tenantId = tenant(header);
        return hierarchyHandler
                .handle(new GetAssetParentQuery(tenantId, AssetId.of(id)))
                .map(parent -> parent.<Response>map(value -> Response.ok(
                                new ParentResponse(value.toString())).build())
                        .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                                .entity(new ErrorResponse("asset.parent.not-found",
                                        "Parent not found for asset: " + id))
                                .build()))
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @GET
    @Path("/{id}/components")
    @Operation(summary = "List the direct hierarchical components of an asset (ASSET-17)")
    public Uni<Response> components(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id
    ) {
        String tenantId = tenant(header);
        return hierarchyHandler
                .handle(new GetAssetComponentsQuery(tenantId, AssetId.of(id)))
                .map(items -> Response.ok(new ComponentsResponse(
                        items.stream().map(UUID::toString).toList())).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    @GET
    @Path("/{id}/hierarchy")
    @Operation(summary = "Get the nested component hierarchy of an asset (ASSET-17)")
    public Uni<Response> hierarchy(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("id") UUID id,
            @QueryParam("depth") @DefaultValue("3") int depth
    ) {
        String tenantId = tenant(header);
        return hierarchyHandler
                .handle(new GetAssetHierarchyQuery(tenantId, AssetId.of(id), depth))
                .map(tree -> Response.ok(HierarchyResponse.from(tree)).build())
                .onFailure().recoverWithItem(AssetResource::toErrorResponse);
    }

    private static Response toErrorResponse(Throwable throwable) {
        Throwable cause = unwrap(throwable);

        if (cause instanceof ApplicationErrorException applicationError) {
            ApplicationError error = applicationError.error();
            int status = switch (error.code()) {
                case "asset.number.duplicate" -> Response.Status.CONFLICT.getStatusCode();
                case "asset.not-found" -> Response.Status.NOT_FOUND.getStatusCode();
                default -> Response.Status.BAD_REQUEST.getStatusCode();
            };
            return Response.status(status)
                    .entity(new ErrorResponse(error.code(), error.message()))
                    .build();
        }
        if (cause instanceof InvalidStateException invalidState) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("asset.invalid-state", invalidState.getMessage()))
                    .build();
        }
        if (cause instanceof BusinessRuleViolation rule) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("asset.rule-violation", rule.getMessage()))
                    .build();
        }
        if (cause instanceof IllegalArgumentException bad) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("asset.invalid-argument", bad.getMessage()))
                    .build();
        }
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("asset.unexpected",
                        cause.getMessage() == null ? "Unexpected error" : cause.getMessage()))
                .build();
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public record CreateAssetRequest(String assetNumber, String name, AssetType type) {
    }

    public record UpdateAssetRequest(String name, AssetType type) {
    }

    public record LocationRequest(String locationId, String locationName) {
    }

    public record CustodyRequest(String partyId, String partyType, String partyName) {
    }

    public record ClassificationRequest(String classificationId, String classificationName) {
    }

    public record RelationshipRequest(UUID relatedAssetId, AssetRelationshipType type) {
    }

    public record MovementResponse(
            String id,
            String assetId,
            String movementType,
            String occurredAt,
            String fromLocationId,
            String fromPartyId,
            String toLocationId,
            String toPartyId,
            String classificationId,
            String relatedAssetId,
            String sourceEventId
    ) {
        static MovementResponse from(tech.kayys.numou.asset.domain.movement.AssetMovement m) {
            return new MovementResponse(
                    m.id().value().toString(),
                    m.assetId().value().toString(),
                    m.type().name(),
                    m.occurredAt().toString(),
                    m.fromLocationId(),
                    m.fromPartyId(),
                    m.toLocationId(),
                    m.toPartyId(),
                    m.classificationId(),
                    m.relatedAssetId() == null ? null : m.relatedAssetId().toString(),
                    m.sourceEventId().toString());
        }
    }

    public record AttributeRequest(
            String key,
            String value,
            tech.kayys.numou.asset.domain.classification.AssetAttributeType type
    ) {
    }

    public record AttributesRequest(java.util.List<AttributeRequest> attributes) {
    }

    public record ErrorResponse(String code, String message) {
    }

    public record ParentResponse(String parentAssetId) {
    }

    public record ComponentsResponse(java.util.List<String> items) {
    }

    public record HierarchyNodeResponse(String assetId, java.util.List<HierarchyNodeResponse> children) {

        static HierarchyNodeResponse from(tech.kayys.numou.asset.application.query.AssetHierarchyNode node) {
            return new HierarchyNodeResponse(node.assetId().toString(),
                    node.children().stream().map(HierarchyNodeResponse::from).toList());
        }
    }

    public record HierarchyResponse(String rootAssetId, java.util.List<HierarchyNodeResponse> children) {

        static HierarchyResponse from(tech.kayys.numou.asset.application.query.AssetHierarchy tree) {
            return new HierarchyResponse(tree.rootAssetId().toString(),
                    tree.children().stream().map(HierarchyNodeResponse::from).toList());
        }
    }
}
