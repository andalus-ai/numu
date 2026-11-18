package tech.kayys.numou.feedback.adapter.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import tech.kayys.numou.survey.domain.DomainException;
import tech.kayys.numou.survey.domain.ErrorCode;

import java.util.Map;

@Provider
public final class PublicSurveyDomainExceptionMapper implements ExceptionMapper<DomainException> {
    @Override
    public Response toResponse(DomainException exception) {
        Response.Status status = switch (exception.code()) {
            case NOT_FOUND -> Response.Status.NOT_FOUND;
            case FORBIDDEN -> Response.Status.FORBIDDEN;
            case CONFLICT, INVALID_STATE -> Response.Status.CONFLICT;
            case VALIDATION_FAILED -> Response.Status.UNPROCESSABLE_ENTITY;
            case LIMIT_EXCEEDED -> Response.Status.TOO_MANY_REQUESTS;
            case TIME_LIMIT_EXCEEDED -> Response.Status.GONE;
        };
        return Response.status(status)
                .entity(Map.of("error", exception.code().name().toLowerCase()))
                .header("Cache-Control", "no-store").build();
    }
}
