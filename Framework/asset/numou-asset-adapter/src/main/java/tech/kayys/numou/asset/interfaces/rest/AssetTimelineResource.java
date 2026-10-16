package tech.kayys.numou.asset.interfaces.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.asset.application.timeline.AssetTimelineCategory;
import tech.kayys.numou.asset.application.timeline.AssetTimelineFilter;
import tech.kayys.numou.asset.application.timeline.AssetTimelineRepository;
import tech.kayys.numou.asset.application.timeline.GetAssetTimelineHandler;
import tech.kayys.numou.asset.application.timeline.GetAssetTimelineQuery;
import tech.kayys.numou.foundation.adapter.context.TenantContext;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * REST adapter exposing the unified asset timeline (ASSET-27 §30).
 *
 * <p>Supports category and time-range filtering plus pagination; the read model
 * is always tenant-scoped.</p>
 */
@Path("/api/v1/assets/{assetId}/timeline")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Timeline API", description = "Unified asset history read model")
public class AssetTimelineResource {

    private final GetAssetTimelineHandler handler;

    @Inject
    public AssetTimelineResource(AssetTimelineRepository timeline) {
        this.handler = new GetAssetTimelineHandler(timeline);
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

    @GET
    @Operation(summary = "Page through the asset timeline")
    public Uni<Response> timeline(
            @HeaderParam("X-Tenant-Id") String header,
            @PathParam("assetId") UUID assetId,
            @QueryParam("category") String category,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size) {
        String tenantId = tenant(header);
        AssetTimelineFilter filter = new AssetTimelineFilter(
                categories(category),
                from == null ? null : Instant.parse(from),
                to == null ? null : Instant.parse(to),
                page,
                size);
        return handler.handle(new GetAssetTimelineQuery(tenantId, assetId, filter))
                .map(result -> Response.ok(result).build())
                .onFailure().recoverWithItem(AssetTimelineResource::toErrorResponse);
    }

    static Set<AssetTimelineCategory> categories(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(part -> AssetTimelineCategory.valueOf(part.toUpperCase()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static Response toErrorResponse(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof ApplicationErrorException app) {
            ApplicationError error = app.error();
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(error.code(), error.message())).build();
        }
        if (cause instanceof BusinessRuleViolation || cause instanceof IllegalArgumentException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("timeline.invalid-argument", cause.getMessage())).build();
        }
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("timeline.unexpected",
                        cause.getMessage() == null ? "Unexpected error" : cause.getMessage())).build();
    }

    private static Throwable unwrap(Throwable th) {
        Throwable current = th;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public record ErrorResponse(String code, String message) {
    }
}