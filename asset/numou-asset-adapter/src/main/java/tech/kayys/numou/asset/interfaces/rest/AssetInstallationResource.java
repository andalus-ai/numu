package tech.kayys.numou.asset.interfaces.rest;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.asset.application.installation.GetInstallationHistoryHandler;
import tech.kayys.numou.asset.application.installation.GetInstallationHistoryQuery;
import tech.kayys.numou.asset.application.installation.InstallAssetComponentCommand;
import tech.kayys.numou.asset.application.installation.InstallAssetComponentHandler;
import tech.kayys.numou.asset.application.installation.RemoveAssetComponentCommand;
import tech.kayys.numou.asset.application.installation.RemoveAssetComponentHandler;
import tech.kayys.numou.asset.domain.installation.AssetInstallation;
import tech.kayys.numou.asset.domain.relationship.AssetRelationshipType;
import tech.kayys.numou.asset.domain.repository.AssetInstallationRepository;
import tech.kayys.numou.asset.domain.repository.AssetRelationshipRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.foundation.adapter.context.TenantContext;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
@Path("/api/v1/assets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Asset Installation API", description = "Component installation endpoints (ASSET-18)")
public class AssetInstallationResource {

    private final InstallAssetComponentHandler installHandler;
    private final RemoveAssetComponentHandler removeHandler;
    private final GetInstallationHistoryHandler historyHandler;

    @Inject
    public AssetInstallationResource(AssetRepository assets, AssetRelationshipRepository relationships,
                                     AssetInstallationRepository installations, EventPublisher publisher,
                                     UnitOfWork unitOfWork, DomainClock clock) {
        this(new InstallAssetComponentHandler(assets, relationships, installations, publisher, unitOfWork, clock),
                new RemoveAssetComponentHandler(relationships, installations, publisher, unitOfWork, clock),
                new GetInstallationHistoryHandler(installations));
    }

    public AssetInstallationResource(InstallAssetComponentHandler installHandler,
                                     RemoveAssetComponentHandler removeHandler,
                                     GetInstallationHistoryHandler historyHandler) {
        this.installHandler = installHandler;
        this.removeHandler = removeHandler;
        this.historyHandler = historyHandler;
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
    @Path("/{id}/installations")
    @Operation(summary = "Install a component on an asset (ASSET-18)")
    public Uni<Response> install(@HeaderParam("X-Tenant-Id") String header, @PathParam("id") UUID id, InstallRequest request) {
        String tenantId = tenant(header);
        return installHandler.handle(new InstallAssetComponentCommand(tenantId, id, request.parentAssetId(), request.relationshipType(), request.installedBy()))
                .map(result -> Response.status(Response.Status.CREATED).entity(result.orElseThrow()).build())
                .onFailure().recoverWithItem(AssetInstallationResource::toErrorResponse);
    }

    @DELETE
    @Path("/{id}/installations")
    @Operation(summary = "Remove a component installation (ASSET-18)")
    public Uni<Response> remove(@HeaderParam("X-Tenant-Id") String header, @PathParam("id") UUID id,
                                @QueryParam("parentAssetId") UUID parentQuery, RemoveRequest body) {
        String tenantId = tenant(header);
        UUID parentId = parentQuery != null ? parentQuery : (body == null ? null : body.parentAssetId());
        String removedBy = body == null ? null : body.removedBy();
        if (parentId == null) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("asset.invalid-argument", "parentAssetId is required")).build());
        }
        return removeHandler.handle(new RemoveAssetComponentCommand(tenantId, id, parentId, removedBy))
                .map(result -> Response.ok(result.orElseThrow()).build())
                .onFailure().recoverWithItem(AssetInstallationResource::toErrorResponse);
    }

    @GET
    @Path("/{id}/installations")
    @Operation(summary = "List installation history of an asset (ASSET-18)")
    public Uni<Response> history(@HeaderParam("X-Tenant-Id") String header, @PathParam("id") UUID id) {
        String tenantId = tenant(header);
        return historyHandler.handle(new GetInstallationHistoryQuery(tenantId, id))
                .map(items -> Response.ok(items.stream().map(InstallationResponse::from).toList()).build())
                .onFailure().recoverWithItem(AssetInstallationResource::toErrorResponse);
    }

    private static Response toErrorResponse(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof ApplicationErrorException applicationError) {
            ApplicationError error = applicationError.error();
            int status = switch (error.code()) {
                case "asset.not-found", "asset.installation.not-found" -> Response.Status.NOT_FOUND.getStatusCode();
                case "asset.installation.conflict", "asset.installation.cycle" -> Response.Status.CONFLICT.getStatusCode();
                default -> Response.Status.BAD_REQUEST.getStatusCode();
            };
            return Response.status(status).entity(new ErrorResponse(error.code(), error.message())).build();
        }
        if (cause instanceof InvalidStateException invalidState) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("asset.invalid-state", invalidState.getMessage())).build();
        }
        if (cause instanceof BusinessRuleViolation rule) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("asset.rule-violation", rule.getMessage())).build();
        }
        if (cause instanceof IllegalArgumentException bad) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("asset.invalid-argument", bad.getMessage())).build();
        }
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("asset.unexpected", cause.getMessage() == null ? "Unexpected error" : cause.getMessage())).build();
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public record InstallRequest(UUID parentAssetId, AssetRelationshipType relationshipType, String installedBy) {
    }

    public record RemoveRequest(UUID parentAssetId, String removedBy) {
    }

    public record InstallationResponse(String id, String componentAssetId, String parentAssetId, String relationshipType, String installedAt, String installedBy, String status, String removedAt, String removedBy) {
        static InstallationResponse from(AssetInstallation m) {
            return new InstallationResponse(m.id().value().toString(), m.componentAssetId().toString(), m.parentAssetId().toString(), m.relationshipType().name(), m.installedAt().toString(), m.installedBy(), m.status().name(), m.removedAt() == null ? null : m.removedAt().toString(), m.removedBy());
        }
    }

    public record ErrorResponse(String code, String message) {
    }
}
