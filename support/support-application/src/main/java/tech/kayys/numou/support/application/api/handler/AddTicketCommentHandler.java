package tech.kayys.numou.support.application.api.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.support.application.api.ActorContext;
import tech.kayys.numou.support.application.api.ActorType;
import tech.kayys.numou.support.application.api.SupportActorProvider;
import tech.kayys.numou.support.application.api.TicketAccessPolicy;
import tech.kayys.numou.support.application.api.command.AddTicketCommentCommand;
import tech.kayys.numou.support.application.port.TicketRepository;
import tech.kayys.numou.support.domain.ticket.Ticket;
import tech.kayys.numou.support.domain.ticket.TicketComment;

import java.util.Objects;
import java.util.UUID;

public final class AddTicketCommentHandler implements CommandHandler<AddTicketCommentCommand, Void> {

    private final TicketRepository ticketRepository;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;
    private final SupportActorProvider actorProvider;
    private final TicketAccessPolicy accessPolicy;

    public AddTicketCommentHandler(TicketRepository ticketRepository, EventPublisher eventPublisher,
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
    public Uni<Void> handle(AddTicketCommentCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        return actorProvider.currentActor().flatMap(actor -> {
            if (actor.userId() == null || !actor.userId().equals(command.authorId())) {
                return notFound();
            }
            if (command.internal() && !mayWriteInternalNote(actor.type())) {
                return notFound();
            }
            return accessPolicy.canComment(command.ticketId(), actor).flatMap(allowed -> {
                if (!allowed) {
                    return notFound();
                }
                return unitOfWork.execute(() -> ticketRepository.findById(command.ticketId())
                        .flatMap(optional -> optional
                                .filter(ticket -> ticket.tenantId().equals(actor.tenantId()))
                                .map(ticket -> persistComment(ticket, command))
                                .orElseGet(this::notFound)));
            });
        });
    }

    private Uni<Void> persistComment(Ticket ticket, AddTicketCommentCommand command) {
        var now = clock.now();
        TicketComment comment = new TicketComment(
                UUID.randomUUID(),
                command.authorId(),
                command.body(),
                command.internal(),
                now
        );
        ticket.addComment(comment, now);
        return ticketRepository.save(ticket)
                .flatMap(saved -> eventPublisher.publish(ticket.pullDomainEvents()));
    }

    private static boolean mayWriteInternalNote(ActorType actorType) {
        return actorType == ActorType.AGENT
                || actorType == ActorType.MANAGER
                || actorType == ActorType.SYSTEM;
    }

    private static <T> Uni<T> notFound() {
        // Deliberately do not distinguish missing tickets from inaccessible tickets.
        return Uni.createFrom().failure(new InvalidStateException("Ticket not found"));
    }
}
