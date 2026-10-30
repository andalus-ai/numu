package tech.kayys.numou.support.domain.ticket;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.support.domain.ticket.event.TicketCreated;
import tech.kayys.numou.support.domain.ticket.event.TicketStatusChanged;

import java.time.Instant;
import java.util.UUID;

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

    @Test
    void customerVisibleCommentsExcludeInternalAgentNotes() {
        Ticket ticket = newTicket();
        UserId agent = UserId.newId();
        TicketComment publicReply = new TicketComment(
                UUID.randomUUID(), agent, "We are looking into this.", false, NOW);
        TicketComment internalNote = new TicketComment(
                UUID.randomUUID(), agent, "Escalate to the fraud team.", true, NOW);

        ticket.addComment(publicReply, NOW);
        ticket.addComment(internalNote, NOW);

        assertEquals(2, ticket.comments().size(),
                "The internal domain history must retain both kinds of comments");
        assertEquals(java.util.List.of(publicReply), ticket.customerVisibleComments(),
                "Customer-facing projections must never include internal notes");
    }

    @Test
    void nullCommentTimestampDoesNotMutateAggregateOrRaiseEvent() {
        Ticket ticket = newTicket();
        TicketComment comment = new TicketComment(
                UUID.randomUUID(), UserId.newId(), "Reply", false, NOW);
        int originalEventCount = ticket.getDomainEvents().size();

        assertThrows(NullPointerException.class, () -> ticket.addComment(comment, null));

        assertEquals(0, ticket.comments().size());
        assertEquals(originalEventCount, ticket.getDomainEvents().size());
    }

    @Test
    void reconstitutionRestoresSnapshotWithoutPublishingCreationEvent() {
        TenantId tenant = TenantId.generate();
        ParticipantId participant = ParticipantId.generate();
        UserId agent = UserId.newId();
        TicketComment publicReply = new TicketComment(
                UUID.randomUUID(), agent, "Public response", false, NOW.plusSeconds(10));
        TicketComment internalNote = new TicketComment(
                UUID.randomUUID(), agent, "Internal handoff", true, NOW.plusSeconds(20));

        Ticket restored = Ticket.reconstitute(
                TicketId.generate(), tenant,
                new Requester.Party(participant, RequesterRole.PARTNER),
                TicketType.INCIDENT, TicketPriority.HIGH, TicketStatus.WAITING,
                WaitingReason.VENDOR, "Service issue", "Details", agent,
                NOW.plusSeconds(5), null, null, NOW, NOW.plusSeconds(20), 7,
                java.util.List.of(publicReply, internalNote));

        assertEquals(tenant, restored.tenantId());
        assertEquals(RequesterRole.PARTNER, ((Requester.Party) restored.requester()).role());
        assertEquals(TicketStatus.WAITING, restored.status());
        assertEquals(WaitingReason.VENDOR, restored.waitingReason());
        assertEquals(agent, restored.assignedAgentId());
        assertEquals(7, restored.getVersion());
        assertEquals(NOW, restored.getCreatedAt());
        assertEquals(NOW.plusSeconds(20), restored.getUpdatedAt());
        assertEquals(java.util.List.of(publicReply, internalNote), restored.comments());
        assertEquals(java.util.List.of(publicReply), restored.customerVisibleComments());
        assertEquals(java.util.List.of(), restored.pullDomainEvents());
    }

    @Test
    void reconstitutionRejectsInconsistentPersistedState() {
        assertThrows(IllegalArgumentException.class, () -> Ticket.reconstitute(
                TicketId.generate(), TenantId.generate(),
                new Requester.Anonymous("external:1"), TicketType.QUESTION,
                TicketPriority.NORMAL, TicketStatus.WAITING, null,
                "Question", "Details", null, null, null, null,
                NOW, NOW, 0, java.util.List.of()));
    }

    private Ticket newTicket() {
        return Ticket.create(TicketId.generate(), TenantId.generate(),
                new Requester.Anonymous("email:customer@example.test"),
                TicketType.QUESTION, TicketPriority.NORMAL, "Question", "Details", NOW);
    }
}
