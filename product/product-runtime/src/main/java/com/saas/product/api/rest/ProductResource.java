package com.saas.product.api.rest;

import com.saas.product.api.dto.ProductDto;
import com.saas.product.api.mapper.ProductDtoMapper;
import com.saas.product.core.ProductAggregate;
import com.saas.product.core.model.ProductCore;
import com.saas.product.core.model.ProductId;
import com.saas.product.core.pricing.PricingContext;
import com.saas.product.core.pricing.PricingResult;
import com.saas.product.service.ProductNotFoundException;
import com.saas.product.service.ProductQueryService;
import com.saas.product.service.ProductService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * Product REST API.
 *
 * Base path: /api/v1/products
 *
 * All operations are tenant-scoped via X-Tenant-Id header.
 * Actor is resolved from the JWT principal (SecurityContext).
 *
 * Convention:
 *  GET    /products              → list (paginated)
 *  GET    /products/{id}         → get by id
 *  POST   /products              → create
 *  PATCH  /products/{id}         → update core
 *  DELETE /products/{id}         → archive (soft delete)
 *
 *  POST   /products/{id}/activate
 *  POST   /products/{id}/suspend
 *  POST   /products/{id}/archive
 *
 *  PUT    /products/{id}/extensions/ecommerce
 *  PUT    /products/{id}/extensions/fnb
 *  PUT    /products/{id}/extensions/subscription
 *  DELETE /products/{id}/extensions/{context}
 *
 *  POST   /products/{id}/price
 *
 *  GET    /products/search?q=...
 */
@Path("/api/v1/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Products", description = "Product catalog management")
public class ProductResource {

    private static final Logger LOG = Logger.getLogger(ProductResource.class);

    @Inject ProductService productService;
    @Inject ProductQueryService queryService;
    @Inject ProductDtoMapper mapper;

    // ── Tenant / Actor resolution ─────────────────────────────────────────

    @Context SecurityContext security;

    private String tenantId(String header) {
        if (header == null || header.isBlank())
            throw new BadRequestException("X-Tenant-Id header is required");
        return header;
    }

    private String actor() {
        var principal = security.getUserPrincipal();
        return principal != null ? principal.getName() : "system";
    }

    // ── List / Search ─────────────────────────────────────────────────────

    @GET
    @Operation(summary = "List all products for a tenant (paginated)")
    @APIResponse(responseCode = "200", description = "Paginated product list")
    public Response list(
            @HeaderParam("X-Tenant-Id") String rawTenantId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("status") String status,
            @QueryParam("category") String categoryId,
            @QueryParam("context") String context,
            @QueryParam("q") String searchQuery) {

        String tenantId = tenantId(rawTenantId);

        var result = (searchQuery != null && !searchQuery.isBlank())
                ? queryService.search(tenantId, searchQuery, page, size)
                : (context != null)
                    ? queryService.listByContext(tenantId, context, page, size)
                    : (categoryId != null)
                        ? queryService.listByCategory(tenantId, categoryId, page, size)
                        : queryService.list(tenantId, page, size);

        return Response.ok(mapper.toPagedResponse(result)).build();
    }

    // ── Get by ID ─────────────────────────────────────────────────────────

    @GET
    @Path("/{id}")
    @Operation(summary = "Get product by ID")
    @APIResponse(responseCode = "200", description = "Product found")
    @APIResponse(responseCode = "404", description = "Product not found")
    public Response getById(
            @PathParam("id") String id,
            @HeaderParam("X-Tenant-Id") String rawTenantId) {

        String tenantId = tenantId(rawTenantId);
        ProductAggregate product = productService.findById(ProductId.of(id), tenantId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
        return Response.ok(mapper.toResponse(product)).build();
    }

    // ── Get by SKU ────────────────────────────────────────────────────────

    @GET
    @Path("/sku/{sku}")
    @Operation(summary = "Get product by SKU")
    public Response getBySku(
            @PathParam("sku") String sku,
            @HeaderParam("X-Tenant-Id") String rawTenantId) {

        String tenantId = tenantId(rawTenantId);
        ProductAggregate product = productService.findBySku(sku, tenantId)
                .orElseThrow(() -> new NotFoundException("Product with SKU not found: " + sku));
        return Response.ok(mapper.toResponse(product)).build();
    }

    // ── Create ────────────────────────────────────────────────────────────

    @POST
    @Operation(summary = "Create a new product (DRAFT state)")
    @APIResponse(responseCode = "201", description = "Product created")
    @APIResponse(responseCode = "409", description = "SKU already exists")
    public Response create(
            @Valid ProductDto.CreateProductRequest req,
            @HeaderParam("X-Tenant-Id") String rawTenantId) {

        String tenantId = tenantId(rawTenantId);
        ProductCore core = mapper.toCoreFromCreate(req, tenantId);
        ProductAggregate created = productService.create(core, actor());
        return Response.status(Response.Status.CREATED)
                .entity(mapper.toResponse(created))
                .build();
    }

    // ── Update Core ───────────────────────────────────────────────────────

    @PATCH
    @Path("/{id}")
    @Operation(summary = "Update product core fields (partial update)")
    @APIResponse(responseCode = "200", description = "Product updated")
    public Response update(
            @PathParam("id") String id,
            @Valid ProductDto.UpdateProductRequest req,
            @HeaderParam("X-Tenant-Id") String rawTenantId) {

        String tenantId = tenantId(rawTenantId);
        ProductAggregate existing = productService.findById(ProductId.of(id), tenantId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
        ProductCore updated = mapper.applyUpdate(existing.getCore(), req);
        ProductAggregate saved = productService.updateCore(
                ProductId.of(id), tenantId, updated, actor());
        return Response.ok(mapper.toResponse(saved)).build();
    }

    // ── Lifecycle: Activate ───────────────────────────────────────────────

    @POST
    @Path("/{id}/activate")
    @Operation(summary = "Activate a DRAFT product (make it orderable)")
    public Response activate(
            @PathParam("id") String id,
            @HeaderParam("X-Tenant-Id") String rawTenantId) {

        String tenantId = tenantId(rawTenantId);
        ProductAggregate product = productService.activate(
                ProductId.of(id), tenantId, actor());
        return Response.ok(mapper.toResponse(product)).build();
    }

    // ── Lifecycle: Suspend ────────────────────────────────────────────────

    @POST
    @Path("/{id}/suspend")
    @Operation(summary = "Temporarily suspend an ACTIVE product")
    public Response suspend(
            @PathParam("id") String id,
            @HeaderParam("X-Tenant-Id") String rawTenantId,
            @QueryParam("reason") @DefaultValue("") String reason) {

        String tenantId = tenantId(rawTenantId);
        ProductAggregate product = productService.suspend(
                ProductId.of(id), tenantId, reason, actor());
        return Response.ok(mapper.toResponse(product)).build();
    }

    // ── Lifecycle: Archive (soft delete) ──────────────────────────────────

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Archive a product (soft delete — irreversible)")
    @APIResponse(responseCode = "204", description = "Product archived")
    public Response archive(
            @PathParam("id") String id,
            @HeaderParam("X-Tenant-Id") String rawTenantId) {

        String tenantId = tenantId(rawTenantId);
        productService.archive(ProductId.of(id), tenantId, actor());
        return Response.noContent().build();
    }

    // ── Extensions ────────────────────────────────────────────────────────

    @PUT
    @Path("/{id}/extensions/ecommerce")
    @Operation(summary = "Attach or replace the e-commerce extension")
    public Response putEcommerceExtension(
            @PathParam("id") String id,
            @Valid ProductDto.EcommerceExtensionRequest req,
            @HeaderParam("X-Tenant-Id") String rawTenantId) {

        String tenantId = tenantId(rawTenantId);
        ProductAggregate product = productService.putExtension(
                ProductId.of(id), tenantId,
                mapper.toEcommerceExtension(req), actor());
        return Response.ok(mapper.toResponse(product)).build();
    }

    @PUT
    @Path("/{id}/extensions/fnb")
    @Operation(summary = "Attach or replace the FnB extension")
    public Response putFnbExtension(
            @PathParam("id") String id,
            @Valid ProductDto.FnbExtensionRequest req,
            @HeaderParam("X-Tenant-Id") String rawTenantId) {

        String tenantId = tenantId(rawTenantId);
        ProductAggregate product = productService.putExtension(
                ProductId.of(id), tenantId,
                mapper.toFnbExtension(req), actor());
        return Response.ok(mapper.toResponse(product)).build();
    }

    @PUT
    @Path("/{id}/extensions/subscription")
    @Operation(summary = "Attach or replace the subscription extension")
    public Response putSubscriptionExtension(
            @PathParam("id") String id,
            @Valid ProductDto.SubscriptionExtensionRequest req,
            @HeaderParam("X-Tenant-Id") String rawTenantId) {

        String tenantId = tenantId(rawTenantId);
        ProductAggregate product = productService.putExtension(
                ProductId.of(id), tenantId,
                mapper.toSubscriptionExtension(req), actor());
        return Response.ok(mapper.toResponse(product)).build();
    }

    @DELETE
    @Path("/{id}/extensions/{context}")
    @Operation(summary = "Remove an extension from a product")
    public Response removeExtension(
            @PathParam("id") String id,
            @PathParam("context") String context,
            @HeaderParam("X-Tenant-Id") String rawTenantId) {

        String tenantId = tenantId(rawTenantId);
        ProductAggregate product = productService.removeExtension(
                ProductId.of(id), tenantId, context, actor());
        return Response.ok(mapper.toResponse(product)).build();
    }

    // ── Pricing ───────────────────────────────────────────────────────────

    @POST
    @Path("/{id}/price")
    @Operation(summary = "Calculate the price for a product in a given context")
    @APIResponse(responseCode = "200", description = "Pricing result with full audit trail")
    public Response calculatePrice(
            @PathParam("id") String id,
            @Valid ProductDto.PriceRequest req,
            @HeaderParam("X-Tenant-Id") String rawTenantId) {

        String tenantId = tenantId(rawTenantId);
        PricingContext pricingCtx = PricingContext.builder(tenantId)
                .customerId(req.customerId())
                .customerSegment(req.customerSegment() != null ? req.customerSegment() : "retail")
                .channelId(req.channelId())
                .quantity(req.quantity())
                .coupons(req.couponCodes())
                .hints(req.hints() != null ? req.hints() : java.util.Map.of())
                .build();

        PricingResult result = productService.calculatePrice(
                ProductId.of(id), req.context(), pricingCtx);

        return Response.ok(mapper.toPriceResponse(id, req.context(), result)).build();
    }

    // ── Exception mapping ─────────────────────────────────────────────────

    @jakarta.ws.rs.ext.Provider
    public static class ProductExceptionMapper
            implements jakarta.ws.rs.ext.ExceptionMapper<ProductNotFoundException> {
        @Override
        public Response toResponse(ProductNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("NOT_FOUND", e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    public record ErrorResponse(String code, String message) {}
}
