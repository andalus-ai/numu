package tech.kayys.numou.support.application.api;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.support.domain.ticket.Requester;
import tech.kayys.numou.support.domain.ticket.RequesterRole;
import tech.kayys.numou.support.domain.ticket.Ticket;
import tech.kayys.numou.support.domain.ticket.TicketComment;
import tech.kayys.numou.support.domain.ticket.TicketId;
import tech.kayys.numou.support.domain.ticket.TicketPriority;
import tech.kayys.numou.support.domain.ticket.TicketType;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TicketResponseTest {

    @Test
    void nonStaffResponsesOmitInternalComments() {
        var now = Instant.now();
        var ticket = Ticket.create(
                TicketId.generate(),
                TenantId.generate(),
                new Requester.Party(ParticipantId.generate(), RequesterRole.CUSTOMER),
                TicketType.QUESTION,
                TicketPriority.NORMAL,
                "Help",
                "Need assistance",
                now);
        ticket.addComment(new TicketComment(UUID.randomUUID(), UserId.newId(),
                "Public update", false, now), now);
        ticket.addComment(new TicketComment(UUID.randomUUID(), UserId.newId(),
                "Internal note", true, now), now);

        var portalResponse = TicketResponse.fromDomain(ticket, ActorType.CUSTOMER);
        var partnerResponse = TicketResponse.fromDomain(ticket, ActorType.PARTNER);
        var agentResponse = TicketResponse.fromDomain(ticket, ActorType.AGENT);

        assertThat(portalResponse.comments()).extracting(TicketResponse.CommentResponse::body)
                .containsExactly("Public update");
        assertThat(partnerResponse.comments()).extracting(TicketResponse.CommentResponse::body)
                .containsExactly("Public update");
        assertThat(agentResponse.comments()).extracting(TicketResponse.CommentResponse::body)
                .containsExactly("Public update", "Internal note");
    }
}
