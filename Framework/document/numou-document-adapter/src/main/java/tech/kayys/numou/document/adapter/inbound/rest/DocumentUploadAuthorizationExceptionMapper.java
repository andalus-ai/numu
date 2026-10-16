package tech.kayys.syirkah.document.adapter.inbound.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class DocumentUploadAuthorizationExceptionMapper implements ExceptionMapper<SecurityException> {
    @Override
    public Response toResponse(SecurityException exception) {
        return Response.status(Response.Status.FORBIDDEN)
                .type(MediaType.APPLICATION_JSON)
                .entity(new DocumentStorageExceptionMapper.StorageError(403, "Upload authorization failed"))
                .build();
    }
}
