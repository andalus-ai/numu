package io.surveyhub.adapter.rest;

import com.fasterxml.jackson.databind.JsonNode;
import io.surveyhub.application.port.in.WebhookUseCase;
import io.surveyhub.application.port.out.WebhookDeliveryLog.Delivery;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.*;

/** Webhook subscriptions of the caller's tenant (needs the MANAGE_INTEGRATIONS permission, i.e. the OWNER role). */
@Path("/api/v1/webhooks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WebhookResource {
    private final WebhookUseCase hooks;
    private final RequestTenant tenant;

    @Inject
    public WebhookResource(WebhookUseCase hooks, RequestTenant tenant) {
        this.hooks = hooks;
        this.tenant = tenant;
    }

    /** Body: {"url":"https://...","events":["response.submitted","survey.published","survey.closed"],"description":"..."} (events optional = all). */
    @POST
    public Response register(JsonNode body) {
        Set<String> events = new LinkedHashSet<>();
        body.path("events").forEach(n -> events.add(n.asText()));
        var r = hooks.register(tenant.get(), body.path("url").asText(null), events, body.path("description").asText(""));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", r.id());
        out.put("url", r.url());
        out.put("events", new TreeSet<>(r.events()));
        out.put("secret", r.secret());
        out.put("note", "Store the secret now. It signs every delivery and is never shown again.");
        return Response.status(201).entity(out).build();
    }

    @GET
    public List<Map<String, Object>> list() {
        return hooks.list(tenant.get()).stream().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.id());
            m.put("url", v.url());
            m.put("events", new TreeSet<>(v.events()));
            m.put("active", v.active());
            m.put("description", v.description());
            m.put("createdAt", v.createdAt().toString());
            return m;
        }).toList();
    }

    @DELETE @Path("{id}")
    public Response delete(@PathParam("id") String id) {
        hooks.delete(tenant.get(), id);
        return Response.status(204).build();
    }

    /** Body: {"active":true|false} */
    @PUT @Path("{id}/active")
    public Map<String, Object> setActive(@PathParam("id") String id, JsonNode body) {
        boolean active = body.path("active").asBoolean(true);
        hooks.setActive(tenant.get(), id, active);
        return Map.of("id", id, "active", active);
    }

    /** Sends one signed webhook.ping and reports the outcome. */
    @POST @Path("{id}/test")
    public Map<String, Object> test(@PathParam("id") String id) {
        var r = hooks.sendTest(tenant.get(), id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", r.success());
        m.put("status", r.status());
        m.put("error", r.error());
        m.put("millis", r.millis());
        return m;
    }

    @GET @Path("deliveries")
    public List<Map<String, Object>> deliveries(@QueryParam("limit") @DefaultValue("50") int limit) {
        return hooks.deliveries(tenant.get(), limit).stream().map(WebhookResource::render).toList();
    }

    private static Map<String, Object> render(Delivery d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deliveryId", d.deliveryId());
        m.put("webhookId", d.webhookId());
        m.put("event", d.eventType());
        m.put("attempt", d.attempt());
        m.put("at", d.at().toString());
        m.put("success", d.success());
        m.put("status", d.status());
        m.put("error", d.error());
        m.put("millis", d.millis());
        return m;
    }
}
