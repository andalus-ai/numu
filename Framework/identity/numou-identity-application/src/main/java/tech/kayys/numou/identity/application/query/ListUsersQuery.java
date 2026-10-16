package tech.kayys.syirkah.identity.application.query;

import tech.kayys.syirkah.foundation.application.page.PageRequest;
import tech.kayys.syirkah.foundation.application.query.Query;

import java.util.Objects;

public record ListUsersQuery(PageRequest page) implements Query {

    public ListUsersQuery {
        Objects.requireNonNull(page, "page cannot be null");
    }

}
