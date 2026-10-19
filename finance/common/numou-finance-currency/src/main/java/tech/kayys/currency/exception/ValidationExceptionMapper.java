package tech.kayys.sy.currency.exception;

import java.util.Map;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    @Override
    public Response toResponse(ConstraintViolationException e) {
        var errors = e.getConstraintViolations().stream()
            .collect(Collectors.toMap(
                v -> v.getPropertyPath().toString(),
                v -> v.getMessage()
            ));
        
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of("errors", errors))
            .build();
    }
}