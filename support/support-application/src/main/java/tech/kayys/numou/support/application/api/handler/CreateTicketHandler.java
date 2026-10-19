package tech.kayys.numou.support.application.api.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.support.application.api.command.CreateTicketCommand;
import tech.kayys.numou.support.application.port.TicketRepository;
import tech.kayys.numou.support.domain.ticket.Ticket;
import tech.kayys.numou.support.domain.ticket.TicketId;

import tech.kayys.numou.foundation.domain.time.DomainClock;
import java.util.Objects;

public final class CreateTicketHandler implements CommandHandler<CreateTicketCommand, TicketId> {

    private final TicketRepository ticketRepository;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;

    public CreateTicketHandler(TicketRepository ticketRepository, EventPublisher eventPublisher,
                               UnitOfWork unitOfWork, DomainClock clock) {
        this.ticketRepository = Objects.requireNonNull(ticketRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Uni<TicketId> handle(CreateTicketCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Ticket ticket = Ticket.create(
                TicketId.generate(),
                command.tenantId(),
                command.requester(),
                command.type(),
                command.priority(),
                command.subject(),
                command.description(),
                clock.now());

        return unitOfWork.execute(() -> ticketRepository.save(ticket)
                .flatMap(saved -> eventPublisher.publish(ticket.pullDomainEvents())
                        .replaceWith(saved.id())));
    }
}
