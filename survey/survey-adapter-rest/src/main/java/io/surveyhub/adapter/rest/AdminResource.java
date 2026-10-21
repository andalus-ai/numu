package io.surveyhub.adapter.rest;

import com.fasterxml.jackson.databind.JsonNode;
import io.surveyhub.application.port.in.TenantAdminUseCase;
import io.surveyhub.domain.DomainException;
import io.surveyhub.domain.ErrorCode;
import io.surveyhub.domain.id.TenantId;
import io.surveyhub.domain.tenant.Plan;
import io.surveyhub.domain.tenant.Tenant;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;

/** Platform-operator API. Protected by a shared key here; replace with real authN/Z (e.g. an operator role). */
@Path("/api/v1/admin/tenants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {
    private final TenantAdminUseCase admin;
    private final String adminKey;

    @Inject
    public AdminResource(TenantAdminUseCase admin, @ConfigProperty(name = "survey.admin-key") String adminKey) {
        this.admin = admin;
        this.adminKey = adminKey;
    }

    @POST
    public Response register(@HeaderParam("X-Admin-Key") String key, JsonNode body) {
        guard(key);
        Tenant t = admin.register(new TenantAdminUseCase.RegisterTenant(new TenantId(body.path("id").asText(null)),
                body.path("name").asText(null), Plan.byName(body.path("plan").asText("free"))));
        return Response.status(201).entity(render(t)).build();
    }

    @GET
    public List<Map<String, Object>> list(@HeaderParam("X-Admin-Key") String key) {
        guard(key);
        return admin.list().stream().map(AdminResource::render).toList();
    }

    @PUT @Path("{id}/plan")
    public Map<String, Object> changePlan(@HeaderParam("X-Admin-Key") String key, @PathParam("id") String id, JsonNode body) {
        guard(key);
        return render(admin.changePlan(new TenantId(id), Plan.byName(body.path("plan").asText(null))));
    }

    @POST @Path("{id}/deactivate")
    public Map<String, Object> deactivate(@HeaderParam("X-Admin-Key") String key, @PathParam("id") String id) {
        guard(key);
        return render(admin.deactivate(new TenantId(id)));
    }

    private void guard(String key) {
        boolean ok = key != null && !adminKey.isBlank()
                && java.security.MessageDigest.isEqual(key.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                adminKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!ok) throw new DomainException(ErrorCode.FORBIDDEN, "Invalid or disabled admin key");
    }

    private static Map<String, Object> render(Tenant t) {
        return Map.of("id", t.id().value(), "name", t.name(), "plan", t.plan().name(), "active", t.active());
    }
}
