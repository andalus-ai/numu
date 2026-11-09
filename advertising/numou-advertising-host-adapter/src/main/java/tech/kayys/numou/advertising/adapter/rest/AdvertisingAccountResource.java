package tech.kayys.numou.advertising.adapter.rest;

import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.advertising.application.AdvertisingAccountService;
import tech.kayys.numou.advertising.domain.*;\nimport tech.kayys.numou.advertising.spi.AdvertisingPage;\nimport tech.kayys.numou.advertising.spi.AdvertisingPageRequest;
import tech.kayys.numou.security.adapter.CurrentSecurity;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Tenant-scoped advertiser and publisher inventory API.
 *
 * Tenant and actor are deliberately excluded from request DTOs. Tenant scope
 * is resolved only from the verified CurrentSecurity context.
 */
@Path("/api/v1/advertising")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Advertising Accounts", description = "Advertiser, publisher and publisher inventory management")
@Blocking
public class AdvertisingAccountResource {
    @Inject AdvertisingAccountService accounts;
    @Inject CurrentSecurity currentSecurity;

    public record RegisterAccountRequest(
            @NotBlank @Size(max=200) String displayName,
            @NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") String partyReference) { }

    public record RegisterPropertyRequest(
            @NotBlank @Size(max=200) String name,
            @NotBlank @Size(max=2048) String canonicalUri) { }

    public record DefinePlacementRequest(
            @NotBlank @Pattern(regexp="[a-z0-9]+(?:[._-][a-z0-9]+)*") @Size(max=100) String code,
            @NotBlank @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max=100) String format) { }

    public record AccountResponse(UUID id, String displayName, String status, long version) { }
    public record PropertyResponse(UUID id, UUID publisherId, String name, String canonicalUri,
                                   String status, long version) { }
    public record PlacementResponse(UUID id, UUID propertyId, String code, String format,
                                    String status, long version) { }

    @POST
    @Path("/advertisers")
    @Operation(summary="Register an advertiser for the authenticated tenant")
    public CompletionStage<Response> registerAdvertiser(@Valid RegisterAccountRequest request) {
        return accounts.registerAdvertiser(tenant(), new PartyReference(request.partyReference()), request.displayName())
                .thenApply(a -> Response.created(URI.create("/api/v1/advertising/advertisers/" + a.id().value()))
                        .entity(new AccountResponse(a.id().value(),a.displayName(),a.status().name(),a.version())).build())
                .exceptionally(this::failure);
    }

    @POST
    @Path("/publishers")
    @Operation(summary="Register a publisher for the authenticated tenant")
    public CompletionStage<Response> registerPublisher(@Valid RegisterAccountRequest request) {
        return accounts.registerPublisher(tenant(), new PartyReference(request.partyReference()), request.displayName())
                .thenApply(p -> Response.created(URI.create("/api/v1/advertising/publishers/" + p.id().value()))
                        .entity(new AccountResponse(p.id().value(),p.displayName(),p.status().name(),p.version())).build())
                .exceptionally(this::failure);
    }

    @POST
    @Path("/advertisers/{advertiserId}/activate")
    @Operation(summary="Activate an advertiser account")
    public CompletionStage<Response> activateAdvertiser(@PathParam("advertiserId") UUID id) {
        return accounts.activateAdvertiser(tenant(),new AdvertisingAccountId(id))
                .thenApply(a -> Response.ok(new AccountResponse(a.id().value(),a.displayName(),a.status().name(),a.version())).build())
                .exceptionally(this::failure);
    }

    @POST
    @Path("/publishers/{publisherId}/activate")
    @Operation(summary="Activate a publisher account")
    public CompletionStage<Response> activatePublisher(@PathParam("publisherId") UUID id) {
        return accounts.activatePublisher(tenant(),new AdvertisingAccountId(id))
                .thenApply(p -> Response.ok(new AccountResponse(p.id().value(),p.displayName(),p.status().name(),p.version())).build())
                .exceptionally(this::failure);
    }

    @POST
    @Path("/publishers/{publisherId}/properties")
    @Operation(summary="Register a publisher website or application property")
    public CompletionStage<Response> registerProperty(@PathParam("publisherId") UUID publisherId,
                                                        @Valid RegisterPropertyRequest request) {
        final URI uri;
        try { uri=URI.create(request.canonicalUri()); }
        catch(IllegalArgumentException invalidUri) { return java.util.concurrent.CompletableFuture.completedFuture(
                Response.status(Response.Status.BAD_REQUEST).entity("canonicalUri must be a valid absolute HTTP(S) URI").build()); }
        return accounts.registerProperty(tenant(),new AdvertisingAccountId(publisherId),request.name(),uri)
                .thenApply(p -> Response.status(Response.Status.CREATED)
                        .entity(new PropertyResponse(p.id().value(),p.publisherId().value(),p.name(),p.canonicalUri().toString(),p.status().name(),p.version())).build())
                .exceptionally(this::failure);
    }

    @POST
    @Path("/properties/{propertyId}/verify")
    @Operation(summary="Verify a publisher property")
    public CompletionStage<Response> verifyProperty(@PathParam("propertyId") UUID propertyId) {
        return accounts.verifyProperty(tenant(),new PublisherPropertyId(propertyId))
                .thenApply(p -> Response.ok(new PropertyResponse(p.id().value(),p.publisherId().value(),p.name(),p.canonicalUri().toString(),p.status().name(),p.version())).build())
                .exceptionally(this::failure);
    }

    @POST
    @Path("/properties/{propertyId}/placements")
    @Operation(summary="Define an ad placement on a verified publisher property")
    public CompletionStage<Response> definePlacement(@PathParam("propertyId") UUID propertyId,
                                                       @Valid DefinePlacementRequest request) {
        return accounts.definePlacement(tenant(),new PublisherPropertyId(propertyId),request.code(),request.format())
                .thenApply(p -> Response.status(Response.Status.CREATED)
                        .entity(new PlacementResponse(p.id().value(),p.propertyId().value(),p.code(),p.format(),p.status().name(),p.version())).build())
                .exceptionally(this::failure);
    }

    @POST
    @Path("/placements/{placementId}/enable")
    @Operation(summary="Enable an ad placement after publisher and property checks")
    public CompletionStage<Response> enablePlacement(@PathParam("placementId") UUID placementId) {
        return accounts.enablePlacement(tenant(),new AdPlacementId(placementId))
                .thenApply(p -> Response.ok(new PlacementResponse(p.id().value(),p.propertyId().value(),p.code(),p.format(),p.status().name(),p.version())).build())
                .exceptionally(this::failure);
    }

    @GET
    @Path("/advertisers")
    @Operation(summary="List advertiser accounts for the authenticated tenant")
    public CompletionStage<Response> listAdvertisers(@QueryParam("page") @DefaultValue("0") int page,
                                                      @QueryParam("size") @DefaultValue("20") int size) {
        return accounts.listAdvertisers(tenant(),pageRequest(page,size))
                .thenApply(result -> Response.ok(page(result,a -> new AccountResponse(a.id().value(),a.displayName(),a.status().name(),a.version()))).build())
                .exceptionally(this::failure);
    }

    @GET
    @Path("/advertisers/{advertiserId}")
    @Operation(summary="Get an advertiser account")
    public CompletionStage<Response> getAdvertiser(@PathParam("advertiserId") UUID id) {
        return accounts.getAdvertiser(tenant(),new AdvertisingAccountId(id))
                .thenApply(found -> found.<Response>map(a -> Response.ok(new AccountResponse(a.id().value(),a.displayName(),a.status().name(),a.version())).build())
                        .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build()))
                .exceptionally(this::failure);
    }

    @GET
    @Path("/publishers")
    @Operation(summary="List publisher accounts for the authenticated tenant")
    public CompletionStage<Response> listPublishers(@QueryParam("page") @DefaultValue("0") int page,
                                                     @QueryParam("size") @DefaultValue("20") int size) {
        return accounts.listPublishers(tenant(),new AdvertisingPageRequest(page,size))
                .thenApply(result -> Response.ok(page(result,p -> new AccountResponse(p.id().value(),p.displayName(),p.status().name(),p.version()))).build())
                .exceptionally(this::failure);
    }

    @GET
    @Path("/publishers/{publisherId}")
    @Operation(summary="Get a publisher account")
    public CompletionStage<Response> getPublisher(@PathParam("publisherId") UUID id) {
        return accounts.getPublisher(tenant(),new AdvertisingAccountId(id))
                .thenApply(found -> found.<Response>map(p -> Response.ok(new AccountResponse(p.id().value(),p.displayName(),p.status().name(),p.version())).build())
                        .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build()))
                .exceptionally(this::failure);
    }

    @GET
    @Path("/publishers/{publisherId}/properties")
    @Operation(summary="List properties owned by a publisher")
    public CompletionStage<Response> listProperties(@PathParam("publisherId") UUID publisherId,
                                                     @QueryParam("page") @DefaultValue("0") int page,
                                                     @QueryParam("size") @DefaultValue("20") int size) {
        return accounts.listProperties(tenant(),new AdvertisingAccountId(publisherId),new AdvertisingPageRequest(page,size))
                .thenApply(result -> Response.ok(page(result,p -> new PropertyResponse(p.id().value(),p.publisherId().value(),p.name(),p.canonicalUri().toString(),p.status().name(),p.version()))).build())
                .exceptionally(this::failure);
    }

    @GET
    @Path("/properties/{propertyId}")
    @Operation(summary="Get a publisher property")
    public CompletionStage<Response> getProperty(@PathParam("propertyId") UUID id) {
        return accounts.getProperty(tenant(),new PublisherPropertyId(id))
                .thenApply(found -> found.<Response>map(p -> Response.ok(new PropertyResponse(p.id().value(),p.publisherId().value(),p.name(),p.canonicalUri().toString(),p.status().name(),p.version())).build())
                        .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build()))
                .exceptionally(this::failure);
    }

    @GET
    @Path("/properties/{propertyId}/placements")
    @Operation(summary="List placements defined on a publisher property")
    public CompletionStage<Response> listPlacements(@PathParam("propertyId") UUID propertyId,
                                                     @QueryParam("page") @DefaultValue("0") int page,
                                                     @QueryParam("size") @DefaultValue("20") int size) {
        return accounts.listPlacements(tenant(),new PublisherPropertyId(propertyId),new AdvertisingPageRequest(page,size))
                .thenApply(result -> Response.ok(page(result,p -> new PlacementResponse(p.id().value(),p.propertyId().value(),p.code(),p.format(),p.status().name(),p.version()))).build())
                .exceptionally(this::failure);
    }

    @GET
    @Path("/placements/{placementId}")
    @Operation(summary="Get an ad placement")
    public CompletionStage<Response> getPlacement(@PathParam("placementId") UUID id) {
        return accounts.getPlacement(tenant(),new AdPlacementId(id))
                .thenApply(found -> found.<Response>map(p -> Response.ok(new PlacementResponse(p.id().value(),p.propertyId().value(),p.code(),p.format(),p.status().name(),p.version())).build())
                        .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build()))
                .exceptionally(this::failure);
    }

    private static <T,R> PageResponse<R> page(AdvertisingPage<T> result,java.util.function.Function<T,R> mapper) {
        return new PageResponse<>(result.items().stream().map(mapper).toList(),result.totalElements(),
                result.page(),result.size(),result.totalPages(),result.hasNext(),result.hasPrevious());
    }

    private static AdvertisingPageRequest pageRequest(int page,int size) {
        try { return new AdvertisingPageRequest(page,size); }
        catch (IllegalArgumentException invalid) { throw new BadRequestException(invalid.getMessage(),invalid); }
    }

    private TenantId tenant() {
        return new TenantId(currentSecurity.require().tenantId().toString());
    }

    private Response failure(Throwable failure) {
        Throwable cause=failure;
        while((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause()!=null)
            cause=cause.getCause();
        if(cause instanceof SecurityException)
            return Response.status(Response.Status.FORBIDDEN).entity("Advertising operation is not authorized").build();
        if(cause instanceof IllegalArgumentException)
            return Response.status(Response.Status.BAD_REQUEST).entity(cause.getMessage()).build();
        if(cause instanceof IllegalStateException)
            return Response.status(Response.Status.CONFLICT).entity(cause.getMessage()).build();
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
}
