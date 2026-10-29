package tech.kayys.numou.support.application.api;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.support.application.port.TicketRepository;
import tech.kayys.numou.support.domain.ticket.Requester;
import tech.kayys.numou.support.domain.ticket.RequesterRole;
import tech.kayys.numou.support.domain.ticket.Ticket;
import tech.kayys.numou.support.domain.ticket.TicketId;
import tech.kayys.numou.support.domain.ticket.TicketStatus;

import java.util.Objects;

/**
 * Repository-backed default policy for support ticket access.
 *
 * Staff actors may access tickets inside their tenant. Portal actors must be the
 * matching requester participant; tenant membership alone never grants access.
 * Application composition must wire this policy only with an authenticated
 * SupportActorProvider and a tenant-scoped TicketRepository.
 */
public final class RepositoryTicketAccessPolicy implements TicketAccessPolicy {

    private final TicketRepository ticketRepository;

    public RepositoryTicketAccessPolicy(TicketRepository ticketRepository) {
        this.ticketRepository = Objects.requireNonNull(ticketRepository, "ticketRepository cannot be null");
    }

    @Override
    public Uni<Boolean> canRead(TicketId ticketId, ActorContext actor) {
        Objects.requireNonNull(actor, "actor cannot be null");
        return findTicket(ticketId).map(optional -> optional
                .filter(ticket -> sameTenant(ticket, actor))
                .filter(ticket -> isStaff(actor.type()) || isRequester(ticket, actor))
                .isPresent());
    }

    @Override
    public Uni<Boolean> canComment(TicketId ticketId, ActorContext actor) {
        Objects.requireNonNull(actor, "actor cannot be null");
        return findTicket(ticketId).map(optional -> optional
                .filter(ticket -> sameTenant(ticket, actor))
                .filter(ticket -> ticket.status() != TicketStatus.CLOSED)
                .filter(ticket -> isStaff(actor.type()) || isRequester(ticket, actor))
                .isPresent());
    }

    @Override
    public Uni<Boolean> canResolve(TicketId ticketId, ActorContext actor) {
        Objects.requireNonNull(actor, "actor cannot be null");
        return findTicket(ticketId).map(optional -> optional
                .filter(ticket -> sameTenant(ticket, actor))
                .filter(ticket -> isStaff(actor.type()))
                .filter(ticket -> ticket.status() != TicketStatus.CLOSED)
                .isPresent());
    }

    private Uni<java.util.Optional<Ticket>> findTicket(TicketId ticketId) {
        Objects.requireNonNull(ticketId, "ticketId cannot be null");
        return ticketRepository.findById(ticketId);
    }

    private static boolean sameTenant(Ticket ticket, ActorContext actor) {
        return ticket.tenantId().equals(actor.tenantId());
    }

    private static boolean isStaff(ActorType type) {
        return type == ActorType.AGENT || type == ActorType.MANAGER || type == ActorType.SYSTEM;
    }

    private static boolean isRequester(Ticket ticket, ActorContext actor) {
        if (actor.participantId() == null || !(ticket.requester() instanceof Requester.Party party)) {
            return false;
        }
        if (!party.participantId().equals(actor.participantId())) {
            return false;
        }
        return switch (actor.type()) {
            case CUSTOMER -> party.role() == RequesterRole.CUSTOMER;
            case PARTNER -> party.role() == RequesterRole.PARTNER;
            case AGENT, MANAGER, SYSTEM -> false;
        };
    }
}
