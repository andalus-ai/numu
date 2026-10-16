package tech.kayys.numou.asset.interfaces.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.asset.application.availability.*;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityReason;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityType;
import tech.kayys.numou.asset.domain.availability.AssetUtilizationType;
import tech.kayys.numou.asset.domain.repository.AssetAvailabilityRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.asset.domain.repository.AssetUtilizationRepository;
import tech.kayys.numou.foundation.adapter.context.TenantContext;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * REST adapter for asset availability and utilization (ASSET-26).
 *
 * <p>Business rules stay in the application/domain layers; this resource only
 * shapes HTTP, resolves the tenant and maps errors.</p>
 */
@Path("/api/v1/assets/{assetId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Availability API", description = "Asset availability windows and utilization")
public class AssetAvailabilityResource {

    private final MarkAssetAvailabilityHandler markHandler;
    private final GetAssetAvailabilityHandler availabilityHandler;
    private final RecordAssetUtilizationHandler utilizationHandler;
    private final GetAssetUtilizationHandler utilizationSummaryHandler;

    @Inject
    public AssetAvailabilityResource(
            AssetRepository assets,
            AssetAvailabilityRepository availability,
            AssetUtilizationRepository utilization,
            EventPublisher publisher,
            UnitOfWork uow,
            DomainClock clock) {
        this.markHandler = new MarkAssetAvailabilityHandler(assets, availability, publisher, uow, clock);
        this.availabilityHandler = new GetAssetAvailabilityHandler(assets, availability, clock);
        this.utilizationHandler = new RecordAssetUtilizationHandler(assets, utilization, publisher, uow, clock);
        this.utilizationSummaryHandler = new GetAssetUtilizationHandler(utilization);
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
    @Path("/availability")
    @Operation(summary = "Mark asset available / unavailable")
    public Uni<Response> mark(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("assetId") UUID assetId,
            MarkRequest req) {
        String tenantId = tenant(header);
        return markHandler.handle(new MarkAssetAvailabilityCommand(
                        tenantId, assetId, req.type(), req.reason(), req.startsAt(), req.endsAt(),
                        req.referenceId(), req.notes()))
                .map(result -> Response.status(Response.Status.CREATED).entity(result.orElseThrow()).build())
                .onFailure().recoverWithItem(AssetAvailabilityResource::toErrorResponse);
    }

    @GET
    @Path("/availability")
    @Operation(summary = "Current availability view")
    public Uni<Response> availability(
            @HeaderParam("X-Tenant-Id") String header, @PathParam("assetId") UUID assetId) {
        String tenantId = tenant(header);
        return availabilityHandler.handle(new GetAssetAvailabilityQuery(tenantId, assetId))
                .map(view -> Response.ok(view).build())
                .onFailure().recoverWithItem(AssetAvailabilityResource::toErrorResponse);
    }

    @POST
    @Path("/utilization")
    @Operation(summary = "Record a utilization observation")
    public Uni<Response> record(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("assetId") UUID assetId,
            UtilizationRequest req) {
        String tenantId = tenant(header);
        return utilizationHandler.handle(new RecordAssetUtilizationCommand(
                        tenantId, assetId, req.startsAt(), req.endsAt(), req.type(), req.quantity(),
                        req.unit(), req.source(), req.referenceId()))
                .map(result -> Response.status(Response.Status.CREATED).entity(result.orElseThrow()).build())
                .onFailure().recoverWithItem(AssetAvailabilityResource::toErrorResponse);
    }

    @GET
    @Path("/utilization")
    @Operation(summary = "Utilization summary over a window")
    public Uni<Response> utilization(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("assetId") UUID assetId,
            @QueryParam("from") String from,
            @QueryParam("to") String to) {
        String tenantId = tenant(header);
        Instant f = from == null ? null : Instant.parse(from);
        Instant t = to == null ? null : Instant.parse(to);
        return utilizationSummaryHandler.handle(new GetAssetUtilizationQuery(tenantId, assetId, f, t))
                .map(summary -> Response.ok(summary).build())
                .onFailure().recoverWithItem(AssetAvailabilityResource::toErrorResponse);
    }

    static Response toErrorResponse(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof ApplicationErrorException app) {
            ApplicationError error = app.error();
            Response.Status status = switch (error.code()) {
                case "asset.not-found" -> Response.Status.NOT_FOUND;
                case "availability.overlap", "availability.asset-disposed" -> Response.Status.CONFLICT;
                default -> Response.Status.BAD_REQUEST;
            };
            return Response.status(status).entity(new ErrorResponse(error.code(), error.message())).build();
        }
        if (cause instanceof BusinessRuleViolation rule) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("availability.rule-violation", rule.getMessage())).build();
        }
        if (cause instanceof IllegalArgumentException bad) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("availability.invalid-argument", bad.getMessage())).build();
        }
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("availability.unexpected",
                        cause.getMessage() == null ? "Unexpected error" : cause.getMessage())).build();
    }

    private static Throwable unwrap(Throwable th) {
        Throwable current = th;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public record MarkRequest(
            AssetAvailabilityType type,
            AssetAvailabilityReason reason,
            Instant startsAt,
            Instant endsAt,
            String referenceId,
            String notes) {
    }

    public record UtilizationRequest(
            Instant startsAt,
            Instant endsAt,
            AssetUtilizationType type,
            BigDecimal quantity,
            String unit,
            String source,
            String referenceId) {
    }

    public record ErrorResponse(String code, String message) {
    }
}