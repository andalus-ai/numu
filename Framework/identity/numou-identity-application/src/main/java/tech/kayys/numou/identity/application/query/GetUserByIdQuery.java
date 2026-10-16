package tech.kayys.numou.identity.application.query;

import tech.kayys.numou.foundation.application.query.Query;

import java.util.Objects;
import java.util.UUID;

public record GetUserByIdQuery(UUID userId) implements Query {

    public GetUserByIdQuery {
        Objects.requireNonNull(userId, "userId cannot be null");
    }

}
