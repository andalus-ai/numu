package tech.kayys.numou.support.application.api.handler;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.support.application.api.ActorContext;
import tech.kayys.numou.support.application.api.ActorType;
import tech.kayys.numou.support.application.api.SupportActorProvider;
import tech.kayys.numou.support.application.api.command.CreatePortalTicketCommand;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreatePortalTicketHandlerTest {

    @Test
    void createsTicketForCustomerUsingVerifiedRequesterIdentity() {
        assertPortalRequesterMapping(ActorType.CUSTOMER, RequesterRole.CUSTOMER);
    }

    @Test
    void createsTicketForPartnerUsingVerifiedRequesterIdentity() {
        assertPortalRequesterMapping(ActorType.PARTNER, RequesterRole.PARTNER);
    }

    @Test
    void rejectsStaffActorsFromPortalCreationPath() {
        var tenant = TenantId.generate();
        var actor = new ActorContext(tenant, ActorType.AGENT, UserId.newId(), null);
        var saved = new AtomicReference<Ticket>();
        var handler = handler(actor, saved);

        assertThatThrownBy(() -> handler.handle(command()).await().indefinitely())
                .isInstanceOf(RuntimeException.class);
        assertThat(saved.get()).isNull();
    }

    @Test
    void rejectsPortalActorWithoutParticipantIdentity() {
        var tenant = TenantId.generate();
        var actor = new ActorContext(tenant, ActorType.PARTNER, UserId.newId(), null);
        var saved = new AtomicReference<Ticket>();
        var handler = handler(actor, saved);

        assertThatThrownBy(() -> handler.handle(command()).await().indefinitely())
                .isInstanceOf(RuntimeException.class);
        assertThat(saved.get()).isNull();
    }

    private static void assertPortalRequesterMapping(ActorType actorType, RequesterRole expectedRole) {
        var tenant = TenantId.generate();
        var participant = ParticipantId.generate();
        var actor = new ActorContext(tenant, actorType, UserId.newId(), participant);
        var saved = new AtomicReference<Ticket>();

        TicketId id = handler(actor, saved).handle(command()).await().indefinitely();

        assertThat(id).isNotNull();
        assertThat(saved.get()).isNotNull();
        assertThat(saved.get().tenantId()).isEqualTo(tenant);
        assertThat(saved.get().requester()).isEqualTo(new Requester.Party(participant, expectedRole));
    }

    private static CreatePortalTicketHandler handler(ActorContext actor, AtomicReference<Ticket> saved) {
        TicketRepository repository = new TicketRepository() {
            @Override public Uni<Ticket> save(Ticket ticket) {
                saved.set(ticket);
                return Uni.createFrom().item(ticket);
            }
            @Override public Uni<Optional<Ticket>> findById(TicketId id) {
                return Uni.createFrom().item(Optional.empty());
            }
            @Override public Uni<List<Ticket>> findByTenantAndRequester(java.util.UUID tenantId,
                    Requester requester, int offset, int limit) {
                return Uni.createFrom().item(List.of());
            }
        };
        EventPublisher events = domainEvents -> Uni.createFrom().voidItem();
        UnitOfWork unitOfWork = new UnitOfWork() {
            @Override public <R> Uni<R> execute(java.util.function.Supplier<Uni<R>> work) {
                return work.get();
            }
        };
        DomainClock clock = () -> Instant.parse("2026-10-30T00:00:00Z");
        CreateTicketHandler createTicket = new CreateTicketHandler(repository, events, unitOfWork, clock);
        SupportActorProvider actorProvider = () -> Uni.createFrom().item(actor);
        return new CreatePortalTicketHandler(actorProvider, createTicket);
    }

    private static CreatePortalTicketCommand command() {
        return new CreatePortalTicketCommand(
                TicketType.QUESTION, TicketPriority.NORMAL, "Portal help", "Please help");
    }
}
