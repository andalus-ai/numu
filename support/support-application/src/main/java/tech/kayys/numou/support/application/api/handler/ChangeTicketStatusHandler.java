package tech.kayys.numou.support.application.api.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.support.application.api.ActorContext;
import tech.kayys.numou.support.application.api.ActorType;
import tech.kayys.numou.support.application.api.SupportActorProvider;
import tech.kayys.numou.support.application.api.TicketAccessPolicy;
import tech.kayys.numou.support.application.api.command.ChangeTicketStatusCommand;
import tech.kayys.numou.support.application.port.TicketRepository;
import tech.kayys.numou.support.domain.ticket.Ticket;

import tech.kayys.numou.foundation.domain.time.DomainClock;
import java.util.Objects;

public final class ChangeTicketStatusHandler
        implements CommandHandler<ChangeTicketStatusCommand, Void> {

    private final TicketRepository ticketRepository;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;
    private final SupportActorProvider actorProvider;
    private final TicketAccessPolicy accessPolicy;

    public ChangeTicketStatusHandler(TicketRepository ticketRepository, EventPublisher eventPublisher,
                                     UnitOfWork unitOfWork, DomainClock clock,
                                     SupportActorProvider actorProvider, TicketAccessPolicy accessPolicy) {
        this.ticketRepository = Objects.requireNonNull(ticketRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
        this.actorProvider = Objects.requireNonNull(actorProvider);
        this.accessPolicy = Objects.requireNonNull(accessPolicy);
    }

    @Override
    public Uni<Void> handle(ChangeTicketStatusCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        return actorProvider.currentActor().flatMap(actor -> {
            if (!isSupportStaff(actor.type())) {
                return notFound();
            }
            Uni<Boolean> permission = (command.status() == tech.kayys.numou.support.domain.ticket.TicketStatus.RESOLVED
                    || command.status() == tech.kayys.numou.support.domain.ticket.TicketStatus.CLOSED)
                    ? accessPolicy.canResolve(command.ticketId(), actor)
                    : Uni.createFrom().item(true);
            return permission.flatMap(allowed -> {
                if (!allowed) {
                    return notFound();
                }
                return unitOfWork.execute(() -> ticketRepository.findById(command.ticketId())
                        .flatMap(optional -> optional
                                .filter(ticket -> ticket.tenantId().equals(actor.tenantId()))
                                .map(ticket -> persistTransition(ticket, command))
                                .orElseGet(this::notFound)));
            });
        });
    }

    private boolean isSupportStaff(ActorType type) {
        return type == ActorType.AGENT || type == ActorType.MANAGER || type == ActorType.SYSTEM;
    }

    private Uni<Void> notFound() {
        return Uni.createFrom().failure(new InvalidStateException("Ticket not found"));
    }

    private Uni<Void> persistTransition(Ticket ticket, ChangeTicketStatusCommand command) {
        ticket.changeStatus(command.status(), command.waitingReason(), clock.now());
        return ticketRepository.save(ticket)
                .flatMap(saved -> eventPublisher.publish(ticket.pullDomainEvents()));
    }
}
