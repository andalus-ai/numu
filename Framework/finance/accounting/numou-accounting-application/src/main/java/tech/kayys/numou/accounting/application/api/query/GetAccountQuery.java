package tech.kayys.numou.accounting.application.api.query;

import tech.kayys.numou.accounting.application.cqrs.Query;
import tech.kayys.numou.accounting.domain.identifier.AccountId;
import tech.kayys.numou.accounting.domain.model.Account;

import java.util.Objects;
import java.util.Optional;

public record GetAccountQuery(
        AccountId id
) implements Query<Optional<Account>> {
    public GetAccountQuery {
        Objects.requireNonNull(id, "id cannot be null");
    }
}
