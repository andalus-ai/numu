package tech.kayys.numou.security.adapter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/** Defensive response headers for an API (no HTML is served, so the CSP is maximally strict). */
@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        final var h = res.getHeaders();
        h.putSingle("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        h.putSingle("X-Content-Type-Options", "nosniff");
        h.putSingle("X-Frame-Options", "DENY");
        h.putSingle("Referrer-Policy", "no-referrer");
        h.putSingle("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        h.putSingle("Permissions-Policy", "geolocation=(), camera=(), microphone=()");
        if (("/" + req.getUriInfo().getPath()).startsWith("/api/")) {
            h.putSingle("Cache-Control", "no-store"); // API responses contain tenant data: never cached by shared proxies
        }
    }
}
