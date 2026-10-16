package tech.kayys.syirkah.document.adapter.inbound.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class DocumentUploadConflictExceptionMapper implements ExceptionMapper<IllegalStateException> {
    @Override
    public Response toResponse(IllegalStateException exception) {
        return Response.status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON)
                .entity(new DocumentStorageExceptionMapper.StorageError(409, exception.getMessage()))
                .build();
    }
}
