package tech.kayys.numou.identity.adapter.inbound.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.UUID;

/**
 * Ensures requests to the /api/v1/ endpoints carry a verified actor ID in X-Actor-Id
 * matched against the authenticated principal.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class VerifiedActorFilter implements ContainerRequestFilter {

    private final SecurityIdentity identity;

    @Inject
    public VerifiedActorFilter(SecurityIdentity identity) {
        this.identity = identity;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        String path = request.getUriInfo().getPath();
        if (!(path.equals("api/v1") || path.startsWith("api/v1/"))) {
            return;
        }
        // Login cannot have a previously issued platform principal.
        if (path.equals("api/v1/auth/login")) {
            return;
        }
        // Federated login endpoints run before authentication exists (they are the login).
        if (path.startsWith("api/v1/auth/federated/")) {
            return;
        }
        if (identity.isAnonymous()) {
            throw new WebApplicationException("Authentication required", 401);
        }

        // Use the authenticated identity, never an unverified caller-provided actor ID.
        String actor = canonicalActor(identity.getPrincipal().getName());
        String supplied = request.getHeaderString("X-Actor-Id");
        if (supplied != null && !actor.equals(supplied)) {
            throw new WebApplicationException("Actor does not match authenticated principal", 403);
        }
        request.getHeaders().putSingle("X-Actor-Id", actor);
    }

    public static String canonicalActor(String subject) {
        try {
            return UUID.fromString(subject).toString();
        } catch (RuntimeException ex) {
            throw new WebApplicationException("Authenticated principal must be a UUID", 403);
        }
    }
}
