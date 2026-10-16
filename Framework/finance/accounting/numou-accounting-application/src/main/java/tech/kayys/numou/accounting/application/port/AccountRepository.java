package tech.kayys.syirkah.accounting.application.port;

import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.model.Account;
import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.Optional;

public interface AccountRepository {
    Uni<Optional<Account>> findById(AccountId id);
    Uni<Optional<Account>> findByAccountNumber(String accountNumber);
    Uni<List<Account>> findAll();
    Uni<Void> save(Account account);
}
