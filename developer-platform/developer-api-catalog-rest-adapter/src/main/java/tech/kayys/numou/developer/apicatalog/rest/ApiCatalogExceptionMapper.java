package tech.kayys.numou.developer.apicatalog.rest;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.UUID;

/** Stable, non-sensitive problem response for transport-level request failures. */
@Provider
public class ApiCatalogExceptionMapper implements ExceptionMapper<Throwable> {
    public record Problem(String type, String title, int status, String detail, String instance) { }

    @Override
    public Response toResponse(Throwable failure) {
        Throwable cause = unwrap(failure);
        int status;
        String title;
        String detail;
        if (cause instanceof ConstraintViolationException || cause instanceof BadRequestException
                || cause instanceof IllegalArgumentException) {
            status = 400; title = "Bad Request"; detail = "The request is invalid.";
        } else if (cause instanceof NotFoundException) {
            status = 404; title = "Not Found"; detail = "The requested resource was not found.";
        } else if (cause instanceof WebApplicationException web) {
            status = web.getResponse().getStatus(); title = status >= 500 ? "Request Failed" : "Request Rejected";
            detail = status >= 500 ? "The request could not be completed." : "The request was rejected.";
        } else {
            status = 500; title = "Internal Server Error"; detail = "The request could not be completed.";
        }
        String instance = "urn:request:" + UUID.randomUUID();
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new Problem("about:blank", title, status, detail, instance)).build();
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
