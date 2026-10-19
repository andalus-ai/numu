package tech.kayys.numou.security.adapter;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import tech.kayys.numou.foundation.adapter.context.CorrelationContext;
import tech.kayys.numou.security.application.problem.Problem;
import tech.kayys.numou.security.application.problem.ProblemMapper;

/**
 * Single place where failures become HTTP responses. 5xx bodies are generic (the detail is logged with the
 * correlation id), which replaces the per-module mapper that echoed {@code cause.getMessage()} to callers.
 * Priority 4000 beats the legacy mapper registered at {@code Priorities.USER} (5000).
 */
@Provider
@Priority(4000)
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class);

    @Inject
    ProblemMapper mapper;

    @Override
    public Response toResponse(Throwable failure) {
        if (failure instanceof WebApplicationException wae && wae.getResponse() != null) {
            final int status = wae.getResponse().getStatus(); // 404 unknown route, 415 media type, 405 ... keep, but do not echo detail
            return ProblemResponses.of(new Problem(status, "http-" + status, Response.Status.fromStatusCode(status) == null
                    ? "HTTP error" : Response.Status.fromStatusCode(status).getReasonPhrase(), "The request could not be processed.", null));
        }
        final Problem problem = mapper.map(failure);
        if (problem.status() >= 500) {
            LOG.errorf(failure, "Unhandled failure correlationId=%s", CorrelationContext.getCorrelationId());
        } else {
            LOG.debugf("Request rejected status=%d code=%s correlationId=%s", problem.status(), problem.code(), CorrelationContext.getCorrelationId());
        }
        return ProblemResponses.of(problem);
    }
}
