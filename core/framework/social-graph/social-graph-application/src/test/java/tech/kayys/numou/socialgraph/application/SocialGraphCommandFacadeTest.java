package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.spi.SocialGraphTransaction;
import tech.kayys.numou.socialgraph.spi.SocialGraphUnitOfWork;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialGraphCommandFacadeTest {
    private final TenantId tenant = TenantId.generate();
    private final IdentitySubjectRef subject = new IdentitySubjectRef("https://issuer.example", "subject-123");

    @Test
    void unassociatedIdentityIsDeniedBeforeCommandExecution() {
        AtomicBoolean unitOfWorkInvoked = new AtomicBoolean();
        SocialGraphCommandFacade facade = facade(
                (requestedTenant, requestedSubject) -> Uni.createFrom().item(Optional.empty()),
                unitOfWorkInvoked, (actor, action) -> {});

        assertThatThrownBy(() -> facade.requestConnection(
                tenant, subject, PartyId.generate(), "request-1").await().indefinitely())
                .isInstanceOf(SocialGraphAccessDeniedException.class);
        assertThat(unitOfWorkInvoked).isFalse();
    }

    @Test
    void resolverCannotSubstituteAnActorFromAnotherTenant() {
        AtomicBoolean unitOfWorkInvoked = new AtomicBoolean();
        TenantId otherTenant = TenantId.generate();
        SocialGraphCommandFacade facade = facade(
                (requestedTenant, requestedSubject) -> Uni.createFrom().item(
                        Optional.of(new SocialGraphActor(otherTenant, PartyId.generate()))),
                unitOfWorkInvoked, (actor, action) -> {});

        assertThatThrownBy(() -> facade.requestConnection(
                tenant, subject, PartyId.generate(), "request-2").await().indefinitely())
                .isInstanceOf(SocialGraphAccessDeniedException.class);
        assertThat(unitOfWorkInvoked).isFalse();
    }

    @Test
    void capabilityAuthorizationRunsAfterResolutionAndBeforePersistence() {
        AtomicBoolean unitOfWorkInvoked = new AtomicBoolean();
        AtomicBoolean authorizationInvoked = new AtomicBoolean();
        SocialGraphCommandFacade facade = facade(
                (requestedTenant, requestedSubject) -> Uni.createFrom().item(
                        Optional.of(new SocialGraphActor(tenant, PartyId.generate()))),
                unitOfWorkInvoked,
                (actor, action) -> {
                    authorizationInvoked.set(true);
                    throw new SocialGraphAccessDeniedException();
                });

        assertThatThrownBy(() -> facade.requestConnection(
                tenant, subject, PartyId.generate(), "request-3").await().indefinitely())
                .isInstanceOf(SocialGraphAccessDeniedException.class);
        assertThat(authorizationInvoked).isTrue();
        assertThat(unitOfWorkInvoked).isFalse();
    }

    private SocialGraphCommandFacade facade(
            SocialGraphActorResolver resolver,
            AtomicBoolean unitOfWorkInvoked,
            SocialGraphAuthorizationPort authorization) {
        SocialGraphUnitOfWork unitOfWork = new SocialGraphUnitOfWork() {
            @Override
            public <T> Uni<T> execute(Function<SocialGraphTransaction, T> work) {
                unitOfWorkInvoked.set(true);
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
