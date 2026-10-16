package tech.kayys.syirkah.document.adapter.inbound.rest;

import java.io.UncheckedIOException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class DocumentStorageIoExceptionMapper implements ExceptionMapper<UncheckedIOException> {
    @Override
    public Response toResponse(UncheckedIOException exception) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(new DocumentStorageExceptionMapper.StorageError(
                        500,
                        "Document storage operation failed"
                ))
                .build();
    }
}
