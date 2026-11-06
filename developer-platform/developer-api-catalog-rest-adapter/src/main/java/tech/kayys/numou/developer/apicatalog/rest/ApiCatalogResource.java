package tech.kayys.numou.developer.apicatalog.rest;

import io.smallrye.mutiny.Uni;
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
import tech.kayys.numou.developer.apicatalog.application.command.*;
import tech.kayys.numou.developer.apicatalog.application.query.*;
import tech.kayys.numou.foundation.application.page.Page;
import tech.kayys.numou.foundation.application.page.PageRequest;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped provider API catalog endpoints. Tenant and actor identity are
 * intentionally absent from request DTOs and are resolved by trusted execution context.
 */
@Path("/api/v1/developer/api-products")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Developer API Catalog")
public class ApiCatalogResource {
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;

    public record CreateProductRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 80) String slug,
            @Size(max = 2000) String description) { }

    public record ScopeRequest(@NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9:._-]{0,99}") String name,
                               @NotBlank @Size(max = 500) String description) { }

    public record AddVersionRequest(
            @NotBlank @Pattern(regexp = "[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?") String version,
            @NotBlank @Pattern(regexp = "3\\.0\\.[0-9]+|3\\.1\\.[0-9]+") String openApiVersion,
            @NotBlank @Size(max = 1024) String artifactReference,
            @NotBlank @Pattern(regexp = "[a-fA-F0-9]{64}") String sha256,
            @Size(max = 100) List<@Valid ScopeRequest> scopes) {
        public AddVersionRequest { scopes = scopes == null ? List.of() : List.copyOf(scopes); }
    }

    public record PageResponse<T>(List<T> content, long totalElements, int page, int size, int totalPages) { }

    @Inject CreateApiProductCommandHandler createProduct;
    @Inject ListApiProductsQueryHandler listProducts;
    @Inject GetApiProductQueryHandler getProduct;
    @Inject AddApiVersionCommandHandler addVersion;
    @Inject PublishApiVersionCommandHandler publishVersion;
    @Inject DeprecateApiVersionCommandHandler deprecateVersion;
    @Inject RetireApiVersionCommandHandler retireVersion;

    @POST
    @Operation(summary = "Register an API product for the authenticated provider tenant")
    public Uni<Response> create(@Valid CreateProductRequest request) {
        return createProduct.handle(new CreateApiProductCommand(request.name(), request.slug(), request.description()))
                .map(id -> Response.created(URI.create("/api/v1/developer/api-products/" + id.value()))
                        .entity(new CreatedProductResponse(id.value())).build());
    }

    public record CreatedProductResponse(UUID id) { }

    @GET
    @Operation(summary = "List API products visible to the authenticated provider tenant")
    public Uni<PageResponse<ApiProductView>> list(@QueryParam("page") @DefaultValue("0") int page,
                                                   @QueryParam("size") @DefaultValue("25") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be non-negative and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return listProducts.handle(new ListApiProductsQuery(PageRequest.of(page, size)))
                .map(result -> pageResponse(result));
    }

    private static PageResponse<ApiProductView> pageResponse(Page<ApiProductView> page) {
        int totalPages = page.size() == 0 ? 0 : (int) Math.ceil((double) page.totalElements() / page.size());
        return new PageResponse<>(page.content(), page.totalElements(), page.page(), page.size(), totalPages);
    }

    @GET
    @Path("/{productId}")
    @Operation(summary = "Get an API product and its version metadata")
    public Uni<ApiProductView> get(@PathParam("productId") UUID productId) {
        return getProduct.handle(new GetApiProductQuery(productId.toString()));
    }

    @POST
    @Path("/{productId}/versions")
    @Operation(summary = "Register a draft API version referencing a validated artifact")
    public Uni<Response> addVersion(@PathParam("productId") UUID productId, @Valid AddVersionRequest request) {
        var scopes = request.scopes().stream()
                .map(scope -> new AddApiVersionCommand.ScopeDefinition(scope.name(), scope.description()))
                .toList();
        var command = new AddApiVersionCommand(productId.toString(), request.version(), request.openApiVersion(),
                request.artifactReference(), request.sha256(), scopes);
        return addVersion.handle(command).replaceWith(Response.status(Response.Status.CREATED).build());
    }

    @POST
    @Path("/{productId}/versions/{version}/publish")
    @Operation(summary = "Validate the stored OpenAPI artifact and publish a version")
    public Uni<Response> publish(@PathParam("productId") UUID productId, @PathParam("version") String version) {
        return publishVersion.handle(new PublishApiVersionCommand(productId.toString(), version))
                .replaceWith(Response.noContent().build());
    }

    @POST
    @Path("/{productId}/versions/{version}/deprecate")
    @Operation(summary = "Deprecate a published API version")
    public Uni<Response> deprecate(@PathParam("productId") UUID productId, @PathParam("version") String version) {
        return deprecateVersion.handle(new DeprecateApiVersionCommand(productId.toString(), version))
                .replaceWith(Response.noContent().build());
    }

    @POST
    @Path("/{productId}/versions/{version}/retire")
    @Operation(summary = "Retire a deprecated API version")
    public Uni<Response> retire(@PathParam("productId") UUID productId, @PathParam("version") String version) {
        return retireVersion.handle(new RetireApiVersionCommand(productId.toString(), version))
                .replaceWith(Response.noContent().build());
    }
}
