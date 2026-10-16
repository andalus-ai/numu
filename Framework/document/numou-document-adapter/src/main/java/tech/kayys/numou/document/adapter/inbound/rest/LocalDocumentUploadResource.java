package tech.kayys.syirkah.document.adapter.inbound.rest;

import io.smallrye.common.annotation.Blocking;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.syirkah.document.adapter.outbound.storage.LocalDocumentStorage;

import java.io.InputStream;
import java.util.UUID;

@Path("/api/v1/document-storage/uploads")
@Produces(MediaType.APPLICATION_JSON)
@UnlessBuildProfile("prod")
public class LocalDocumentUploadResource {
    private final LocalDocumentStorage storage;

    @Inject
    public LocalDocumentUploadResource(LocalDocumentStorage storage) {
        this.storage = storage;
    }

    @PUT
    @Path("/{tenantId}/{uploadSessionId}")
    @Consumes(MediaType.WILDCARD)
    @Blocking
    public Response upload(
            @PathParam("tenantId") UUID tenantId,
            @PathParam("uploadSessionId") UUID uploadSessionId,
            @jakarta.ws.rs.HeaderParam("X-Upload-Token") String token,
            @jakarta.ws.rs.HeaderParam("Content-Type") String contentType,
            InputStream body
    ) {
        storage.acceptUpload(tenantId.toString(), uploadSessionId.toString(), token, contentType, body);
        return Response.noContent().build();
    }
}
