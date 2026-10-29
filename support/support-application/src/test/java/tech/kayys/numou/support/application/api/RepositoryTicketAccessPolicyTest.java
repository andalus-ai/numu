package tech.kayys.numou.support.application.api;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.support.application.port.TicketRepository;
import tech.kayys.numou.support.domain.ticket.Requester;
import tech.kayys.numou.support.domain.ticket.RequesterRole;
import tech.kayys.numou.support.domain.ticket.Ticket;
import tech.kayys.numou.support.domain.ticket.TicketId;
import tech.kayys.numou.support.domain.ticket.TicketPriority;
import tech.kayys.numou.support.domain.ticket.TicketType;
import tech.kayys.numou.support.domain.ticket.TicketStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryTicketAccessPolicyTest {

    @Test
    void portalActorCanReadOnlyTheirOwnTicket() {
        var tenant = TenantId.generate();
        var owner = ParticipantId.generate();
        var ticket = ticket(tenant, owner, RequesterRole.CUSTOMER);
        var policy = policy(ticket);

        assertThat(policy.canRead(ticket.id(), actor(tenant, ActorType.CUSTOMER, owner))
                .await().indefinitely()).isTrue();
        assertThat(policy.canRead(ticket.id(), actor(tenant, ActorType.CUSTOMER, ParticipantId.generate()))
                .await().indefinitely()).isFalse();
    }

    @Test
    void tenantMembershipDoesNotGrantPortalAccessToAnotherTenantTicket() {
        var ticket = ticket(TenantId.generate(), ParticipantId.generate(), RequesterRole.CUSTOMER);
        var policy = policy(ticket);

        assertThat(policy.canRead(ticket.id(), actor(TenantId.generate(), ActorType.AGENT, null))
                .await().indefinitely()).isFalse();
    }

    @Test
    void partnerMustMatchBothParticipantAndRequesterRole() {
        var tenant = TenantId.generate();
        var participant = ParticipantId.generate();
        var ticket = ticket(tenant, participant, RequesterRole.PARTNER);
        var policy = policy(ticket);

        assertThat(policy.canRead(ticket.id(), actor(tenant, ActorType.PARTNER, participant))
                .await().indefinitely()).isTrue();
        assertThat(policy.canRead(ticket.id(), actor(tenant, ActorType.CUSTOMER, participant))
                .await().indefinitely()).isFalse();
    }

    @Test
    void onlyStaffCanResolveAndClosedTicketsCannotBeCommentedOn() {
        var tenant = TenantId.generate();
        var ticket = ticket(tenant, ParticipantId.generate(), RequesterRole.CUSTOMER);
        var policy = policy(ticket);
        var customer = actor(tenant, ActorType.CUSTOMER, ((Requester.Party) ticket.requester()).participantId());
        var agent = actor(tenant, ActorType.AGENT, null);

        assertThat(policy.canResolve(ticket.id(), customer).await().indefinitely()).isFalse();
        assertThat(policy.canResolve(ticket.id(), agent).await().indefinitely()).isTrue();

        ticket.changeStatus(TicketStatus.CLOSED, null, Instant.now());
        assertThat(policy.canComment(ticket.id(), agent).await().indefinitely()).isFalse();
    }

    private static RepositoryTicketAccessPolicy policy(Ticket ticket) {
        TicketRepository repository = new TicketRepository() {
            @Override
            public Uni<Ticket> save(Ticket value) {
                return Uni.createFrom().item(value);
            }

            @Override
            public Uni<Optional<Ticket>> findById(TicketId id) {
                return Uni.createFrom().item(id.equals(ticket.id()) ? Optional.of(ticket) : Optional.empty());
            }

            @Override
            public Uni<List<Ticket>> findByTenantAndRequester(java.util.UUID tenantId, Requester requester,
                                                               int offset, int limit) {
                return Uni.createFrom().item(List.of());
            }
        };
        return new RepositoryTicketAccessPolicy(repository);
    }

    private static ActorContext actor(TenantId tenant, ActorType type, ParticipantId participant) {
        return new ActorContext(tenant, type, UserId.newId(), participant);
    }

    private static Ticket ticket(TenantId tenant, ParticipantId participant, RequesterRole role) {
        return Ticket.create(TicketId.generate(), tenant, new Requester.Party(participant, role),
                TicketType.QUESTION, TicketPriority.NORMAL, "Help", "Need assistance", Instant.now());
    }
}
