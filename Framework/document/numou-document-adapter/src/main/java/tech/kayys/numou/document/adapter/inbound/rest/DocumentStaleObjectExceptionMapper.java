package tech.kayys.syirkah.document.adapter.inbound.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.hibernate.StaleObjectStateException;

@Provider
public class DocumentStaleObjectExceptionMapper
        implements ExceptionMapper<StaleObjectStateException> {
    @Override
    public Response toResponse(StaleObjectStateException exception) {
        return DocumentOptimisticLockExceptionMapper.conflict(exception.getMessage());
    }
}
