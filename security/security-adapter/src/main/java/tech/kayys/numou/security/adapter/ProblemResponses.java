package tech.kayys.numou.security.adapter;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.numou.foundation.adapter.context.CorrelationContext;
import tech.kayys.numou.security.application.problem.Problem;

import java.util.LinkedHashMap;

/** Builds {@code application/problem+json} responses, always carrying the correlation id. */
final class ProblemResponses {

    static final String PROBLEM_JSON = "application/problem+json";

    private ProblemResponses() { }

    static Response of(Problem p) {
        final var body = new LinkedHashMap<String, Object>();
        body.put("status", p.status());
        body.put("code", p.code());
        body.put("title", p.title());
        body.put("detail", p.detail());
        body.put("correlationId", CorrelationContext.getCorrelationId());
        body.putAll(p.extensions());
        return Response.status(p.status()).type(MediaType.valueOf(PROBLEM_JSON)).entity(body).build();
    }
}
