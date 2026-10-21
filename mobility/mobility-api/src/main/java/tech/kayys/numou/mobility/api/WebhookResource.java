package tech.kayys.numou.mobility.api;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.numou.mobility.wallet.application.PayoutService;
import tech.kayys.numou.mobility.wallet.application.TopUpService;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Provider callbacks. No login: authenticity comes from the provider's signature, checked before anything else; the tenant is read from the
 * order reference we issued. Everything except a bad signature answers 200 (so providers stop retrying), including unknown orders. Rate-limited per IP.
 * NOT COMPILED in the authoring sandbox.
 */
@Path("/api/v1/webhooks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.WILDCARD)
public class WebhookResource {

    @Inject TopUpService topUps;
    @Inject PayoutService payouts;

    @POST
    @Path("/payments/{provider}")
    public Uni<Response> payment(@PathParam("provider") String provider, @Context HttpHeaders headers, String body) {
        return topUps.onNotification(provider, lower(headers), body).map(outcome -> Response.ok(Map.of("status", "ok")).build());
    }

    @POST
    @Path("/payouts")
    public Uni<Response> payout(@Context HttpHeaders headers, String body) {
        return payouts.handleCallback(lower(headers), body).replaceWith(Response.ok(Map.of("status", "ok")).build());
    }

    private static Map<String, String> lower(HttpHeaders headers) {
        final Map<String, String> m = new HashMap<>();
        headers.getRequestHeaders().forEach((k, v) -> { if (!v.isEmpty()) m.put(k.toLowerCase(Locale.ROOT), v.get(0)); });
        return m;
    }
}
