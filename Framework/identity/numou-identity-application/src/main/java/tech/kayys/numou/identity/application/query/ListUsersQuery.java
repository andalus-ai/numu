package tech.kayys.numou.identity.application.query;

import tech.kayys.numou.foundation.application.page.PageRequest;
import tech.kayys.numou.foundation.application.query.Query;

import java.util.Objects;

public record ListUsersQuery(PageRequest page) implements Query {

    public ListUsersQuery {
        Objects.requireNonNull(page, "page cannot be null");
    }

}
