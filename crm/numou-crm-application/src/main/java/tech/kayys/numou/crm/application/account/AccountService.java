package tech.kayys.numou.crm.application.account;

import tech.kayys.numou.crm.application.participant.ParticipantPort;
import tech.kayys.numou.crm.domain.account.Account;
import tech.kayys.numou.crm.domain.identifier.AccountId;
import tech.kayys.numou.crm.domain.repository.AccountRepository;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Application boundary for creating CRM accounts from an existing Party.
 */
public final class AccountService {

    private final AccountRepository accountRepository;
    private final ParticipantPort participantPort;

    public AccountService(AccountRepository accountRepository, ParticipantPort participantPort) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.participantPort = Objects.requireNonNull(participantPort);
    }

    public CompletionStage<AccountId> create(CreateAccountCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Objects.requireNonNull(command.participantId(), "participantId cannot be null");

        return participantPort.exists(command.participantId()).thenCompose(exists -> {
            if (!exists) {
                return failed(new IllegalArgumentException(
                        "Participant does not exist: " + command.participantId()));
            }

            Account account = Account.create(
                    AccountId.generate(),
                    command.participantId(),
                    command.name());
            return accountRepository.save(account).thenApply(Account::id);
        });
    }

    private static <T> CompletionStage<T> failed(Throwable error) {
        return java.util.concurrent.CompletableFuture.failedStage(error);
    }
}
