package tech.kayys.numou.support.application.api.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
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

    public ChangeTicketStatusHandler(TicketRepository ticketRepository, EventPublisher eventPublisher,
                                     UnitOfWork unitOfWork, DomainClock clock) {
        this.ticketRepository = Objects.requireNonNull(ticketRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Uni<Void> handle(ChangeTicketStatusCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        return unitOfWork.execute(() -> ticketRepository.findById(command.ticketId())
                .flatMap(optional -> optional
                        .map(ticket -> persistTransition(ticket, command))
                        .orElseGet(() -> Uni.createFrom().failure(
                                new InvalidStateException("Ticket not found: " + command.ticketId())))));
    }

    private Uni<Void> persistTransition(Ticket ticket, ChangeTicketStatusCommand command) {
        ticket.changeStatus(command.status(), command.waitingReason(), clock.now());
        return ticketRepository.save(ticket)
                .flatMap(saved -> eventPublisher.publish(ticket.pullDomainEvents()));
    }
}
