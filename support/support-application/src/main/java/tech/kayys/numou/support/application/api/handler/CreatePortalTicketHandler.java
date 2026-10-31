package tech.kayys.numou.support.application.api.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.support.application.api.ActorType;
import tech.kayys.numou.support.application.api.SupportActorProvider;
import tech.kayys.numou.support.application.api.command.CreatePortalTicketCommand;
import tech.kayys.numou.support.application.api.command.CreateTicketCommand;
import tech.kayys.numou.support.domain.ticket.Requester;
import tech.kayys.numou.support.domain.ticket.RequesterRole;
import tech.kayys.numou.support.domain.ticket.TicketId;

import java.util.Objects;

public final class CreatePortalTicketHandler
        implements CommandHandler<CreatePortalTicketCommand, TicketId> {

    private final SupportActorProvider actorProvider;
    private final CreateTicketHandler createTicketHandler;

    public CreatePortalTicketHandler(SupportActorProvider actorProvider,
                                     CreateTicketHandler createTicketHandler) {
        this.actorProvider = Objects.requireNonNull(actorProvider);
        this.createTicketHandler = Objects.requireNonNull(createTicketHandler);
    }

    @Override
    public Uni<TicketId> handle(CreatePortalTicketCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        return actorProvider.currentActor().flatMap(actor -> {
            RequesterRole requesterRole = switch (actor.type()) {
                case CUSTOMER -> RequesterRole.CUSTOMER;
                case PARTNER -> RequesterRole.PARTNER;
                case AGENT, MANAGER, SYSTEM -> null;
            };
            if (requesterRole == null || actor.participantId() == null) {
                return Uni.createFrom().failure(
                        new BusinessRuleViolation("Portal ticket requires an authenticated customer or partner"));
            }
            var create = new CreateTicketCommand(
                    actor.tenantId(),
                    new Requester.Party(actor.participantId(), requesterRole),
                    command.type(),
                    command.priority(),
                    command.subject(),
                    command.description());
            return createTicketHandler.handle(create);
        });
    }
}
