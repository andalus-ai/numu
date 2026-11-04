package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialConnection;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Trusted entry point: tenant and issuer/subject must come from verified host context. */
public final class SocialGraphCommandFacade {
    private final SocialGraphActorResolver actorResolver;
    private final SocialGraphService service;

    public SocialGraphCommandFacade(SocialGraphActorResolver actorResolver, SocialGraphService service) {
        this.actorResolver = Objects.requireNonNull(actorResolver, "actorResolver cannot be null");
        this.service = Objects.requireNonNull(service, "service cannot be null");
    }

    public Uni<SocialConnection> requestConnection(TenantId tenant, IdentitySubjectRef subject, PartyId recipient, String key) {
        return withActor(tenant, subject, actor -> service.requestConnection(actor, recipient, key));
    }
    public Uni<SocialConnection> acceptConnection(TenantId tenant, IdentitySubjectRef subject, SocialConnectionId id, String key) {
        return withActor(tenant, subject, actor -> service.acceptConnection(actor, id, key));
    }
    public Uni<SocialConnection> rejectConnection(TenantId tenant, IdentitySubjectRef subject, SocialConnectionId id, String key) {
        return withActor(tenant, subject, actor -> service.rejectConnection(actor, id, key));
    }
    public Uni<SocialConnection> cancelConnection(TenantId tenant, IdentitySubjectRef subject, SocialConnectionId id, String key) {
        return withActor(tenant, subject, actor -> service.cancelConnection(actor, id, key));
    }
    public Uni<SocialConnection> removeConnection(TenantId tenant, IdentitySubjectRef subject, SocialConnectionId id, String key) {
        return withActor(tenant, subject, actor -> service.removeConnection(actor, id, key));
    }

    private <T> Uni<T> withActor(TenantId tenant, IdentitySubjectRef subject, Function<SocialGraphActor, Uni<T>> command) {
        Objects.requireNonNull(tenant, "tenant cannot be null");
        Objects.requireNonNull(subject, "subject cannot be null");
        Objects.requireNonNull(command, "command cannot be null");
        return actorResolver.resolve(tenant, subject).onItem().transformToUni(resolved -> {
            if (resolved == null || resolved.isEmpty() || !tenant.equals(resolved.get().tenantId())) {
                return Uni.createFrom().failure(new SocialGraphAccessDeniedException());
            }
            return command.apply(resolved.get());
        });
    }
}
