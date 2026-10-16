package tech.kayys.syirkah.document.adapter.inbound.rest;

import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class DocumentOptimisticLockExceptionMapper
        implements ExceptionMapper<OptimisticLockException> {
    @Override
    public Response toResponse(OptimisticLockException exception) {
        return conflict(exception.getMessage());
    }

    static Response conflict(String message) {
        return Response.status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON)
                .entity(new DocumentUploadExceptionMapper.ErrorResponse("upload_conflict", message))
                .build();
    }
}
