package tech.kayys.numou.support.application.api.handler;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.support.application.api.ActorContext;
import tech.kayys.numou.support.application.api.ActorType;
import tech.kayys.numou.support.application.api.SupportActorProvider;
import tech.kayys.numou.support.application.api.TicketAccessPolicy;
import tech.kayys.numou.support.application.api.TicketResponse;
import tech.kayys.numou.support.application.api.query.GetPortalTicketQuery;
import tech.kayys.numou.support.application.port.TicketRepository;
import tech.kayys.numou.support.domain.ticket.Requester;
import tech.kayys.numou.support.domain.ticket.RequesterRole;
import tech.kayys.numou.support.domain.ticket.Ticket;
import tech.kayys.numou.support.domain.ticket.TicketId;
import tech.kayys.numou.support.domain.ticket.TicketPriority;
import tech.kayys.numou.support.domain.ticket.TicketType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetPortalTicketHandlerTest {

    @Test
    void deniesTicketWhenAccessPolicyRejectsActorEvenWithinSameTenant() {
        var tenant = TenantId.generate();
        var actor = customer(tenant, ParticipantId.generate());
        var ticket = ticket(tenant, ParticipantId.generate());
        var repositoryCalled = new AtomicBoolean();
        var handler = handler(actor, false, ticket, repositoryCalled);

        assertThatThrownBy(() -> handler.handle(new GetPortalTicketQuery(ticket.id())).await().indefinitely())
                .isInstanceOf(RuntimeException.class);
        assertThat(repositoryCalled).isFalse();
    }

    @Test
    void deniesTicketFromAnotherTenantEvenWhenPolicyAllowsIt() {
        var actor = customer(TenantId.generate(), ParticipantId.generate());
        var ticket = ticket(TenantId.generate(), ParticipantId.generate());
        var repositoryCalled = new AtomicBoolean();
        var handler = handler(actor, true, ticket, repositoryCalled);

        assertThatThrownBy(() -> handler.handle(new GetPortalTicketQuery(ticket.id())).await().indefinitely())
                .isInstanceOf(RuntimeException.class);
        assertThat(repositoryCalled).isTrue();
    }

    @Test
    void returnsResponseOnlyAfterPolicyAndTenantChecksPass() {
        var tenant = TenantId.generate();
        var actor = customer(tenant, ParticipantId.generate());
        var ticket = ticket(tenant, actor.participantId());
        var repositoryCalled = new AtomicBoolean();
        var handler = handler(actor, true, ticket, repositoryCalled);

        TicketResponse response = handler.handle(new GetPortalTicketQuery(ticket.id())).await().indefinitely();

        assertThat(repositoryCalled).isTrue();
        assertThat(response).isNotNull();
    }

    private static GetPortalTicketHandler handler(ActorContext actor, boolean allowed, Ticket ticket,
                                                   AtomicBoolean repositoryCalled) {
        TicketRepository repository = new TicketRepository() {
            @Override
            public Uni<Ticket> save(Ticket value) {
                return Uni.createFrom().item(value);
            }

            @Override
            public Uni<Optional<Ticket>> findById(TicketId id) {
                repositoryCalled.set(true);
                return Uni.createFrom().item(id.equals(ticket.id()) ? Optional.of(ticket) : Optional.empty());
            }

            @Override
            public Uni<List<Ticket>> findByTenantAndRequester(java.util.UUID tenantId, Requester requester,
                                                               int offset, int limit) {
                return Uni.createFrom().item(List.of());
            }
        };
        SupportActorProvider actorProvider = () -> Uni.createFrom().item(actor);
        TicketAccessPolicy policy = new TicketAccessPolicy() {
            @Override
            public Uni<Boolean> canRead(TicketId ticketId, ActorContext context) {
                return Uni.createFrom().item(allowed);
            }

            @Override
            public Uni<Boolean> canComment(TicketId ticketId, ActorContext context) {
                return Uni.createFrom().item(false);
            }

            @Override
            public Uni<Boolean> canResolve(TicketId ticketId, ActorContext context) {
                return Uni.createFrom().item(false);
            }
        };
        return new GetPortalTicketHandler(repository, actorProvider, policy);
    }

    private static ActorContext customer(TenantId tenant, ParticipantId participant) {
        return new ActorContext(tenant, ActorType.CUSTOMER, UserId.newId(), participant);
    }

    private static Ticket ticket(TenantId tenant, ParticipantId requester) {
        return Ticket.create(TicketId.generate(), tenant,
                new Requester.Party(requester, RequesterRole.CUSTOMER),
                TicketType.QUESTION, TicketPriority.NORMAL, "Help", "Need assistance", Instant.now());
    }
}
