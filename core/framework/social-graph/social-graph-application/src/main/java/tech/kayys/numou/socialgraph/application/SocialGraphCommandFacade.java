package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialConnection;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;
import tech.kayys.numou.socialgraph.domain.SocialRelationship;
import tech.kayys.numou.socialgraph.spi.SocialGraphUnitOfWork;

import java.time.Clock;

import java.util.Objects;
import java.util.function.Function;

/**
 * Trusted application entry point for Social Graph commands.
 *
 * <p>The host must pass tenant and issuer/subject values obtained from its
 * verified security context, never from command-body fields. This facade
 * resolves the canonical Party for that subject and tenant before delegating
 * to {@link SocialGraphService}, which performs capability authorization and
 * domain lifecycle validation.</p>
 *
 * <p>Keep the underlying service behind the host composition boundary; expose
 * this facade to HTTP, messaging, and other untrusted input adapters.</p>
 * <p>Only call this facade with tenant and issuer/subject values obtained from
 * the host's verified security context. The facade resolves the subject to
 * an active canonical Party before invoking the command service. Tenant
 * membership and capability authorization are independently enforced by
 * SocialGraphService.</p>
 */
public final class SocialGraphCommandFacade {
    private final SocialGraphActorResolver actorResolver;
    private final SocialGraphService service;

    /**
     * Creates the application facade while keeping the command service package-private.
     * Infrastructure hosts compose through this factory rather than depending on
     * the service implementation or weakening its visibility.
     */
    public static SocialGraphCommandFacade compose(
            SocialGraphUnitOfWork unitOfWork,
            PartyDirectoryPort parties,
            SocialGraphAuthorizationPort authorization,
            SocialGraphActorResolver actorResolver,
            Clock clock) {
        SocialGraphService service = new SocialGraphService(unitOfWork, parties, authorization, clock);
        return new SocialGraphCommandFacade(actorResolver, service);
    }

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

    public Uni<SocialRelationship> follow(TenantId tenant, IdentitySubjectRef subject, PartyId target, String key) {
        return withActor(tenant, subject, actor -> service.follow(actor, target, key));
    }

    public Uni<SocialRelationship> unfollow(TenantId tenant, IdentitySubjectRef subject, PartyId target, String key) {
        return withActor(tenant, subject, actor -> service.unfollow(actor, target, key));
    }

    public Uni<SocialRelationship> block(TenantId tenant, IdentitySubjectRef subject, PartyId target, String key) {
        return withActor(tenant, subject, actor -> service.block(actor, target, key));
    }

    public Uni<SocialRelationship> unblock(TenantId tenant, IdentitySubjectRef subject, PartyId target, String key) {
        return withActor(tenant, subject, actor -> service.unblock(actor, target, key));
    }

    public Uni<SocialRelationship> mute(TenantId tenant, IdentitySubjectRef subject, PartyId target, String key) {
        return withActor(tenant, subject, actor -> service.mute(actor, target, key));
    }

    public Uni<SocialRelationship> unmute(TenantId tenant, IdentitySubjectRef subject, PartyId target, String key) {
        return withActor(tenant, subject, actor -> service.unmute(actor, target, key));
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
                    // Defend the composition boundary even if a resolver adapter is misconfigured.
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
