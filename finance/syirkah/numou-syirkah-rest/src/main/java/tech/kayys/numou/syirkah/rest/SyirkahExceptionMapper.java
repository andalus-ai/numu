package tech.kayys.numou.numourest;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import tech.kayys.numou.foundation.adapter.rest.ErrorResponse;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.numouapplication.port.SyirkahRepository;
import tech.kayys.numou.numoudomain.SyirkahNotFoundException;


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
        int status = statusOf(t);
        if (status == 500) {
            String ref = java.util.UUID.randomUUID().toString();
            LOG.errorf(t, "Unhandled syirkah failure, ref=%s", ref);
            return body(500, "INTERNAL_ERROR", "Unexpected failure, reference " + ref);
        }
        return body(status, CODES.get(status), t.getMessage());
    }

    private static final java.util.Map<Integer, String> CODES = java.util.Map.of(400, "BAD_REQUEST",
            403, "FORBIDDEN", 404, "NOT_FOUND", 409, "CONFLICT", 422, "RULE_VIOLATION");

    /** Pure classification, kept separate from the HTTP types so it is testable without a JAX-RS runtime. */
    static int statusOf(Throwable t) {
        if (t instanceof SyirkahNotFoundException) {
            return 404;
        }
        if (t instanceof BusinessRuleViolation) {
            String m = t.getMessage() == null ? "" : t.getMessage();
            return m.contains(" already ") ? 409 : 422;
        }
        if (t instanceof InvalidStateException || t instanceof IllegalStateException
                || t instanceof SyirkahRepository.ConcurrentModificationException) {
            return 409;
        }
        if (t instanceof IllegalArgumentException) {
            return 400;
        }
        if (t instanceof SecurityException) {
            return 403;
        }
        return 500;
    }

    private static Response body(int status, String code, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(code, message == null ? code : message, status)).build();
    }
}
