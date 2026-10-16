package tech.kayys.sy.currency.exception;

import java.util.Map;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {
    @Override
    public Response toResponse(BusinessException e) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of("error", e.getMessage()))
            .build();
    }
}
