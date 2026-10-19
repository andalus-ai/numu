package com.saas.product.api.rest;

import com.saas.product.core.ProductLifecycleException;
import com.saas.product.infrastructure.jpa.ConcurrentProductModificationException;
import com.saas.product.service.DuplicateSkuException;
import com.saas.product.spi.ProductValidationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Maps domain exceptions to appropriate HTTP responses.
 *
 * Registered as JAX-RS providers via @Provider.
 * Add new mappings here as new exceptions are introduced.
 */
public class GlobalExceptionMapper {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    // ── 400 Bad Request ───────────────────────────────────────────────────

    @Provider
    public static class ValidationExceptionMapper
            implements ExceptionMapper<ProductValidationException> {
        @Override
        public Response toResponse(ProductValidationException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "code",       "VALIDATION_ERROR",
                            "message",    "Product validation failed",
                            "violations", e.getViolations()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    @Provider
    public static class LifecycleExceptionMapper
            implements ExceptionMapper<ProductLifecycleException> {
        @Override
        public Response toResponse(ProductLifecycleException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "code",    "LIFECYCLE_ERROR",
                            "message", e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    // ── 409 Conflict ──────────────────────────────────────────────────────

    @Provider
    public static class DuplicateSkuMapper
            implements ExceptionMapper<DuplicateSkuException> {
        @Override
        public Response toResponse(DuplicateSkuException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of(
                            "code",    "DUPLICATE_SKU",
                            "message", e.getMessage(),
                            "sku",     e.getSku()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    @Provider
    public static class ConcurrentModificationMapper
            implements ExceptionMapper<ConcurrentProductModificationException> {
        @Override
        public Response toResponse(ConcurrentProductModificationException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of(
                            "code",      "CONCURRENT_MODIFICATION",
                            "message",   e.getMessage(),
                            "productId", e.getProductId().getValue()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    // ── 500 Internal Server Error ─────────────────────────────────────────

    @Provider
    public static class UnhandledExceptionMapper
            implements ExceptionMapper<Exception> {
        @Override
        public Response toResponse(Exception e) {
            LOG.errorf(e, "Unhandled exception: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                            "code",    "INTERNAL_ERROR",
                            "message", "An unexpected error occurred"))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }
}
