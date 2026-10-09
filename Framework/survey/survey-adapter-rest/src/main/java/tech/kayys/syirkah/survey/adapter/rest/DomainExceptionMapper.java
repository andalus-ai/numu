package tech.kayys.syirkah.survey.adapter.rest;

import tech.kayys.syirkah.survey.domain.DomainException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

@Provider
public class DomainExceptionMapper implements ExceptionMapper<DomainException> {
    @Override
    public Response toResponse(DomainException e) {
        int status = switch (e.code()) {
            case NOT_FOUND -> 404;
            case INVALID_STATE, CONFLICT -> 409;
            case VALIDATION_FAILED -> 422;
            case LIMIT_EXCEEDED -> 402;
            case FORBIDDEN -> 403;
            case TIME_LIMIT_EXCEEDED -> 410;
        };
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", e.code().name());
        body.put("message", e.getMessage());
        if (!e.details().isEmpty()) body.put("details", e.details());
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(body).build();
    }
}
