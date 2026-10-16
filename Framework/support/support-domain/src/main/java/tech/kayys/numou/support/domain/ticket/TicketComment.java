package tech.kayys.numou.support.domain.ticket;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;
import tech.kayys.numou.identity.domain.user.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TicketComment(
        UUID commentId,
        UserId authorId,
        String body,
        boolean internal,
        Instant createdAt
) implements ValueObject {

    public TicketComment {
        Objects.requireNonNull(commentId, "commentId cannot be null");
        Objects.requireNonNull(authorId, "authorId cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body cannot be blank");
        }
        body = body.trim();
    }
}
