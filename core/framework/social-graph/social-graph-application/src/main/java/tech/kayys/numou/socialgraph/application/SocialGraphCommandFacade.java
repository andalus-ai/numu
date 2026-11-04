package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialConnection;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;

import java.util.Objects;
import java.util.function.Function;

/**
 * Trusted application entry point for Social Graph commands.
 *
 * <p>Only call this facade with tenant and issuer/subject values obtained from
 * the host's verified security context. The facade resolves the subject to
 * an active canonical Party before invoking the command service. Tenant
 * membership and capability authorization are independently enforced by
 * SocialGraphService.</p>
 */
public final class SocialGraphCommandFacade {
    private final SocialGraphActorResolver actorResolver;
    private final SocialGraphService service;

    public SocialGraphCommandFacade(SocialGraphActorResolver actorResolver, SocialGraphService service) {
        this.actorResolver = Objects.requireNonNull(actorResolver, "actorResolver cannot be null");
        this.service = Objects.requireNonNull(service, "service cannot be null");
    }

    public Uni<SocialConnection> requestConnection(
            TenantId trustedTenant, IdentitySubjectRef authenticatedSubject,
            PartyId recipient, String idempotencyKey) {
        return withActor(trustedTenant, authenticatedSubject,
                actor -> service.requestConnection(actor, recipient, idempotencyKey));
    }

    public Uni<SocialConnection> acceptConnection(
            TenantId trustedTenant, IdentitySubjectRef authenticatedSubject,
            SocialConnectionId connectionId, String idempotencyKey) {
        return withActor(trustedTenant, authenticatedSubject,
                actor -> service.acceptConnection(actor, connectionId, idempotencyKey));
    }

    public Uni<SocialConnection> rejectConnection(
            TenantId trustedTenant, IdentitySubjectRef authenticatedSubject,
            SocialConnectionId connectionId, String idempotencyKey) {
        return withActor(trustedTenant, authenticatedSubject,
                actor -> service.rejectConnection(actor, connectionId, idempotencyKey));
    }

    public Uni<SocialConnection> cancelConnection(
            TenantId trustedTenant, IdentitySubjectRef authenticatedSubject,
            SocialConnectionId connectionId, String idempotencyKey) {
        return withActor(trustedTenant, authenticatedSubject,
                actor -> service.cancelConnection(actor, connectionId, idempotencyKey));
    }

    public Uni<SocialConnection> removeConnection(
            TenantId trustedTenant, IdentitySubjectRef authenticatedSubject,
            SocialConnectionId connectionId, String idempotencyKey) {
        return withActor(trustedTenant, authenticatedSubject,
                actor -> service.removeConnection(actor, connectionId, idempotencyKey));
    }

    private <T> Uni<T> withActor(
            TenantId trustedTenant, IdentitySubjectRef authenticatedSubject,
            Function<SocialGraphActor, Uni<T>> command) {
        Objects.requireNonNull(trustedTenant, "trustedTenant cannot be null");
        Objects.requireNonNull(authenticatedSubject, "authenticatedSubject cannot be null");
        Objects.requireNonNull(command, "command cannot be null");

        return actorResolver.resolve(trustedTenant, authenticatedSubject)
                .onItem().transformToUni(resolved -> {
                    if (resolved == null || resolved.isEmpty()) {
                        return denied();
                    }
                    SocialGraphActor actor = resolved.get();
                    if (!trustedTenant.equals(actor.tenantId())) {
                        return denied();
                    }
                    return command.apply(actor);
                });
    }

    private static <T> Uni<T> denied() {
        return Uni.createFrom().failure(new SocialGraphAccessDeniedException());
    }
}
