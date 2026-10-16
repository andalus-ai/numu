package tech.kayys.numou.asset.interfaces.rest;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Global fallback exception mapper producing the standardized {@link ApiError}
 * body (ASSET-28 §error semantics).
 *
 * <p>Endpoints that already shape their own errors keep doing so; this adapter
 * guarantees every <em>other</em> failure still leaves the API with a stable,
 * machine-readable contract instead of a container default page.</p>
 */
@Provider
@Priority(Priorities.USER)
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable exception) {
        Throwable cause = unwrap(exception);
        if (cause instanceof WebApplicationException web) {
            return web.getResponse();
        }
        if (cause instanceof ApplicationErrorException app) {
            ApplicationError error = app.error();
            return build(error.code(), error.message(), statusFor(error.code()));
        }
        if (cause instanceof BusinessRuleViolation || cause instanceof IllegalArgumentException) {
            return build("asset.invalid-argument", cause.getMessage(), Response.Status.BAD_REQUEST);
        }
        return build("asset.internal-error",
                cause.getMessage() == null ? "Unexpected error" : cause.getMessage(),
                Response.Status.INTERNAL_SERVER_ERROR);
    }

    private static Response.Status statusFor(String code) {
        if (code.endsWith("not-found")) {
            return Response.Status.NOT_FOUND;
        }
        if (code.contains("conflict") || code.contains("overlap") || code.contains("violation")) {
            return Response.Status.CONFLICT;
        }
        return Response.Status.BAD_REQUEST;
    }

    private static Response build(String code, String message, Response.Status status) {
        return Response.status(status)
                .entity(ApiError.of(code, message, status.getStatusCode()))
                .build();
    }

    private static Throwable unwrap(Throwable th) {
        Throwable current = th;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}