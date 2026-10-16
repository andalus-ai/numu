package tech.kayys.numou.support.domain.ticket;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.support.domain.ticket.event.TicketCreated;
import tech.kayys.numou.support.domain.ticket.event.TicketStatusChanged;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicketTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createRaisesEventAndKeepsRequesterAsParticipantReference() {
        Requester requester = new Requester.Party(ParticipantId.generate(), RequesterRole.CUSTOMER);
        Ticket ticket = Ticket.create(TicketId.generate(), TenantId.generate(), requester,
                TicketType.QUESTION, TicketPriority.NORMAL, "  Help  ", "  Need help  ", NOW);

        assertEquals(TicketStatus.NEW, ticket.status());
        assertEquals("Help", ticket.subject());
        assertEquals(requester, ticket.requester());
        assertInstanceOf(TicketCreated.class, ticket.getDomainEvents().getFirst());
    }

    @Test
    void waitingRequiresAReasonAndLegalStatusTransitionsAreEnforced() {
        Ticket ticket = newTicket();
        assertThrows(IllegalArgumentException.class,
                () -> ticket.changeStatus(TicketStatus.WAITING, null, NOW));
        assertThrows(InvalidStateException.class,
                () -> ticket.changeStatus(TicketStatus.RESOLVED, null, NOW));

        ticket.changeStatus(TicketStatus.OPEN, null, NOW);
        ticket.changeStatus(TicketStatus.WAITING, WaitingReason.CUSTOMER, NOW);
        assertEquals(WaitingReason.CUSTOMER, ticket.waitingReason());
        assertEquals(TicketStatus.WAITING,
                ((TicketStatusChanged) ticket.getDomainEvents().getLast()).newStatus());
    }

    @Test
    void resolvedTicketCanReopenButClosedTicketCannotChange() {
        Ticket ticket = newTicket();
        ticket.changeStatus(TicketStatus.OPEN, null, NOW);
        ticket.changeStatus(TicketStatus.RESOLVED, null, NOW);
        ticket.changeStatus(TicketStatus.REOPENED, null, NOW);
        assertEquals(TicketStatus.REOPENED, ticket.status());

        ticket.changeStatus(TicketStatus.OPEN, null, NOW);
        ticket.changeStatus(TicketStatus.CLOSED, null, NOW);
        assertThrows(InvalidStateException.class, () -> ticket.assign(UserId.newId(), NOW));
    }

    private Ticket newTicket() {
        return Ticket.create(TicketId.generate(), TenantId.generate(),
                new Requester.Anonymous("email:customer@example.test"),
                TicketType.QUESTION, TicketPriority.NORMAL, "Question", "Details", NOW);
    }
}
