package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;
import tech.kayys.numou.socialgraph.spi.SocialGraphTransaction;
import tech.kayys.numou.socialgraph.spi.SocialGraphUnitOfWork;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialGraphCommandFacadeTest {
    private final TenantId tenant = TenantId.generate();
    private final IdentitySubjectRef subject = new IdentitySubjectRef(
            "https://identity.example", "actor-123");

    @Test
    void missingIdentityAssociationIsDeniedBeforePersistence() {
        AtomicInteger persistenceCalls = new AtomicInteger();
        SocialGraphCommandFacade facade = facade(
                (requestedTenant, requestedSubject) -> Uni.createFrom().item(Optional.empty()),
                (actor, action) -> { throw new AssertionError("Authorization must not run without an actor"); },
                persistenceCalls);

        assertThatThrownBy(() -> facade.requestConnection(
                tenant, subject, PartyId.generate(), "missing-identity").await().indefinitely())
                .isInstanceOf(SocialGraphAccessDeniedException.class);
        assertThat(persistenceCalls).hasValue(0);
    }

    @Test
    void crossTenantResolverResultIsDeniedBeforeAuthorization() {
        AtomicInteger persistenceCalls = new AtomicInteger();
        SocialGraphCommandFacade facade = facade(
                (requestedTenant, requestedSubject) -> Uni.createFrom().item(
                        Optional.of(new SocialGraphActor(TenantId.generate(), PartyId.generate()))),
                (actor, action) -> { throw new AssertionError("Authorization must not run for a cross-tenant actor"); },
                persistenceCalls);

        assertThatThrownBy(() -> facade.requestConnection(
                tenant, subject, PartyId.generate(), "cross-tenant").await().indefinitely())
                .isInstanceOf(SocialGraphAccessDeniedException.class);
        assertThat(persistenceCalls).hasValue(0);
    }

    @Test
    void everyCommandResolvesIdentityAndRequiresCapabilityAuthorization() {
        AtomicInteger resolverCalls = new AtomicInteger();
        AtomicInteger authorizationCalls = new AtomicInteger();
        AtomicInteger persistenceCalls = new AtomicInteger();

        SocialGraphActorResolver resolver = (requestedTenant, requestedSubject) -> {
            resolverCalls.incrementAndGet();
            return Uni.createFrom().item(Optional.of(
                    new SocialGraphActor(tenant, PartyId.generate())));
        };
        SocialGraphCommandFacade facade = facade(
                resolver,
                (actor, action) -> {
                    authorizationCalls.incrementAndGet();
                    throw new SocialGraphAccessDeniedException();
                },
                persistenceCalls);

        assertDenied(() -> facade.requestConnection(
                tenant, subject, PartyId.generate(), "request").await().indefinitely());
        assertDenied(() -> facade.acceptConnection(
                tenant, subject, SocialConnectionId.generate(), "accept").await().indefinitely());
        assertDenied(() -> facade.rejectConnection(
                tenant, subject, SocialConnectionId.generate(), "reject").await().indefinitely());
        assertDenied(() -> facade.cancelConnection(
                tenant, subject, SocialConnectionId.generate(), "cancel").await().indefinitely());
        assertDenied(() -> facade.removeConnection(
                tenant, subject, SocialConnectionId.generate(), "remove").await().indefinitely());

        assertThat(resolverCalls).hasValue(5);
        assertThat(authorizationCalls).hasValue(5);
        assertThat(persistenceCalls).hasValue(0);
    }

    private static void assertDenied(Runnable command) {
        assertThatThrownBy(command::run).isInstanceOf(SocialGraphAccessDeniedException.class);
    }

    private SocialGraphCommandFacade facade(
            SocialGraphActorResolver resolver,
            SocialGraphAuthorizationPort authorization,
            AtomicInteger persistenceCalls) {
        SocialGraphUnitOfWork unitOfWork = new SocialGraphUnitOfWork() {
            @Override
            public <T> Uni<T> execute(Function<SocialGraphTransaction, T> work) {
                persistenceCalls.incrementAndGet();
                return Uni.createFrom().failure(new AssertionError("Persistence must not run in this test"));
            }
        };
        SocialGraphService service = new SocialGraphService(
                unitOfWork,
                partyId -> Uni.createFrom().item(true),
                authorization,
                Clock.systemUTC());
        return new SocialGraphCommandFacade(resolver, service);
    }
}
