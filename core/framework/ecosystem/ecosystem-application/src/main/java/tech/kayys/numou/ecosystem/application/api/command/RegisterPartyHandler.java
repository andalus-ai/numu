package tech.kayys.numou.ecosystem.application.api.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.application.port.EcosystemTransactionRunner;
import tech.kayys.numou.ecosystem.domain.model.Party;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;

/** Creates canonical Party identity and appends its event in the same transaction. */
public final class RegisterPartyHandler
        implements CommandHandler<RegisterPartyCommand, Result<RegisterPartyResult>> {

    private final EcosystemTransactionRunner transactions;

    public RegisterPartyHandler(EcosystemTransactionRunner transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions cannot be null");
    }

    @Override
    public Uni<Result<RegisterPartyResult>> handle(RegisterPartyCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        return transactions.inTransaction(transaction -> {
            Party party = Party.register(PartyId.generate(), command.kind());
            var events = party.pullDomainEvents();
            return transaction.parties().save(party)
                    .flatMap(saved -> transaction.events().publish(events)
                            .replaceWith(Result.success(new RegisterPartyResult(
                                    saved.getId(), saved.kind(), saved.status()))));
        });
    }
}
