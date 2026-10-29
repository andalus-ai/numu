package tech.kayys.numou.support.application.api;

import tech.kayys.numou.support.domain.ticket.Ticket;
import tech.kayys.numou.support.domain.ticket.TicketComment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        String subject,
        String description,
        String type,
        String priority,
        String status,
        UUID requesterParticipantId,
        UUID assignedAgentId,
        Instant createdAt,
        Instant updatedAt,
        List<CommentResponse> comments
) {
    public TicketResponse {
        comments = List.copyOf(comments);
    }

    public static TicketResponse fromDomain(Ticket ticket, ActorType actorType) {
        UUID requesterId = ticket.requester() instanceof tech.kayys.numou.support.domain.ticket.Requester.Party party
                ? party.participantId().value() : null;
        List<CommentResponse> visibleComments = ticket.comments().stream()
                .filter(comment -> mayViewInternalComments(actorType) || !comment.internal())
                .map(CommentResponse::fromDomain)
                .toList();
        return new TicketResponse(
                ticket.id().value(),
                ticket.subject(),
                ticket.description(),
                ticket.type().name(),
                ticket.priority().name(),
                ticket.status().name(),
                requesterId,
                ticket.assignedAgentId() == null ? null : ticket.assignedAgentId().value(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                visibleComments);
    }

    private static boolean mayViewInternalComments(ActorType actorType) {
        return actorType == ActorType.AGENT
                || actorType == ActorType.MANAGER
                || actorType == ActorType.SYSTEM;
    }

    public record CommentResponse(UUID id, String body, Instant createdAt) {
        private static CommentResponse fromDomain(TicketComment comment) {
            return new CommentResponse(comment.commentId(), comment.body(), comment.createdAt());
        }
    }
}
