package tech.kayys.numou.workspace.adapter.rest;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.WorkspaceApi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class WorkspaceApiExceptionMapperTest {
    private final WorkspaceApiExceptionMapper mapper = new WorkspaceApiExceptionMapper();

    @Test
    void mapsRevisionConflictToHttp409AndPreservesSafeMessage() {
        try (Response response = mapper.toResponse(new WorkspaceApi.ApiException(409, "revision conflict"))) {
            assertEquals(409, response.getStatus());
            assertInstanceOf(WorkspaceApiExceptionMapper.ErrorResponse.class, response.getEntity());
            var body = (WorkspaceApiExceptionMapper.ErrorResponse) response.getEntity();
            assertEquals(409, body.status());
            assertEquals("revision conflict", body.message());
        }
    }

    @Test
    void doesNotExposeServerErrorDetails() {
        try (Response response = mapper.toResponse(new WorkspaceApi.ApiException(500, "database password leaked"))) {
            assertEquals(500, response.getStatus());
            var body = (WorkspaceApiExceptionMapper.ErrorResponse) response.getEntity();
            assertEquals("Workspace request failed", body.message());
        }
    }
}
