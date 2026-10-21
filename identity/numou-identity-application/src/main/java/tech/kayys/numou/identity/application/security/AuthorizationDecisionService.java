package tech.kayys.numou.identity.application.security;

/** Pure policy decision helper. Callers must supply verified memberships and grants. */
public class AuthorizationDecisionService {

    public boolean isAllowed(AuthorizationRequest request) {
        if (request == null || !request.hasVerifiedScope()) return false;
        if (request.action() == null || request.action().isBlank()) return false;
        if (request.resource() == null || request.resource().isBlank()) return false;
        // Explicit action grants only. Never treat wildcard or authentication as permission.
        return request.grantedActions().contains(request.action());
    }
}
