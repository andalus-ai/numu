package tech.kayys.numou.support.application.api.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.support.application.api.ActorType;
import tech.kayys.numou.support.application.api.SupportActorProvider;
import tech.kayys.numou.support.application.api.command.ChangeTicketPriorityCommand;
import tech.kayys.numou.support.application.port.TicketRepository;
import tech.kayys.numou.support.domain.ticket.Ticket;

import tech.kayys.numou.foundation.domain.time.DomainClock;
import java.util.Objects;

public final class ChangeTicketPriorityHandler implements CommandHandler<ChangeTicketPriorityCommand, Void> {

    private final TicketRepository ticketRepository;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;
    private final SupportActorProvider actorProvider;

    public ChangeTicketPriorityHandler(TicketRepository ticketRepository, EventPublisher eventPublisher,
                                       UnitOfWork unitOfWork, DomainClock clock, SupportActorProvider actorProvider) {
        this.ticketRepository = Objects.requireNonNull(ticketRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
        this.actorProvider = Objects.requireNonNull(actorProvider);
    }

    @Override
    public Uni<Void> handle(ChangeTicketPriorityCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        return actorProvider.currentActor().flatMap(actor -> {
            if (actor.type() != ActorType.AGENT && actor.type() != ActorType.MANAGER
                    && actor.type() != ActorType.SYSTEM) {
                return notFound();
            }
            return unitOfWork.execute(() -> ticketRepository.findById(command.ticketId())
                    .flatMap(optional -> optional
                            .filter(ticket -> ticket.tenantId().equals(actor.tenantId()))
                            .map(ticket -> persistPriorityChange(ticket, command))
                            .orElseGet(this::notFound)));
        });
    }

    private Uni<Void> notFound() {
        return Uni.createFrom().failure(new InvalidStateException("Ticket not found"));
    }

    private Uni<Void> persistPriorityChange(Ticket ticket, ChangeTicketPriorityCommand command) {
        ticket.changePriority(command.priority(), clock.now());
        return ticketRepository.save(ticket)
                .flatMap(saved -> eventPublisher.publish(ticket.pullDomainEvents()));
    }
}
