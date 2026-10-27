package tech.kayys.numou.syirkah.rest;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import tech.kayys.numou.foundation.adapter.rest.ErrorResponse;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.syirkah.application.port.SyirkahRepository;

import java.util.NoSuchElementException;

/**
 * One place that turns failures into HTTP. Rule violations are the caller's to fix (422); stale or conflicting
 * state is retryable after a re-read (409); anything unexpected is logged with a correlation reference and returned
 * without internals.
 */
@Provider
public class SyirkahExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(SyirkahExceptionMapper.class);

    @Override
    public Response toResponse(Throwable t) {
        if (t instanceof WebApplicationException w) {
            return w.getResponse();
        }
        if (t instanceof BusinessRuleViolation) {
            String m = t.getMessage() == null ? "" : t.getMessage();
            if (m.contains(" not found") || m.startsWith("unknown ")) {
                return body(404, "NOT_FOUND", m);
            }
            if (m.contains(" already ")) {
                return body(409, "CONFLICT", m);
            }
            return body(422, "RULE_VIOLATION", m);
        }
        if (t instanceof InvalidStateException || t instanceof IllegalStateException
                || t instanceof SyirkahRepository.ConcurrentModificationException) {
            return body(409, "CONFLICT", t.getMessage());
        }
        if (t instanceof NoSuchElementException) {
            return body(404, "NOT_FOUND", t.getMessage());
        }
        if (t instanceof IllegalArgumentException) {
            return body(400, "BAD_REQUEST", t.getMessage());
        }
        if (t instanceof SecurityException) {
            return body(403, "FORBIDDEN", t.getMessage());
        }
        String ref = java.util.UUID.randomUUID().toString();
        LOG.errorf(t, "Unhandled syirkah failure, ref=%s", ref);
        return body(500, "INTERNAL_ERROR", "Unexpected failure, reference " + ref);
    }

    private static Response body(int status, String code, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(code, message == null ? code : message, status)).build();
    }
}
