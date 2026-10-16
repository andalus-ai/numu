package tech.kayys.numou.support.application.api;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.support.domain.ticket.TicketId;

public interface TicketAccessPolicy {

    Uni<Boolean> canRead(TicketId ticketId, ActorContext actor);

    Uni<Boolean> canComment(TicketId ticketId, ActorContext actor);

    Uni<Boolean> canResolve(TicketId ticketId, ActorContext actor);
}
