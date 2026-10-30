package tech.kayys.numou.ecosystem.application.api.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.application.port.EcosystemTransactionRunner;
import tech.kayys.numou.ecosystem.domain.organization.Organization;
import tech.kayys.numou.ecosystem.domain.party.PartyKind;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;

/** Validates Party identity, persists the profile and appends its event atomically. */
public final class RegisterOrganizationHandler
        implements CommandHandler<RegisterOrganizationCommand, Result<RegisterOrganizationResult>> {

    private final EcosystemTransactionRunner transactions;

    public RegisterOrganizationHandler(EcosystemTransactionRunner transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions cannot be null");
    }

    @Override
    public Uni<Result<RegisterOrganizationResult>> handle(RegisterOrganizationCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        return transactions.inTransaction(transaction -> transaction.parties().findParty(command.partyId())
                .flatMap(party -> {
                    if (party.isEmpty()) {
                        return Uni.createFrom().item(Result.<RegisterOrganizationResult>failure(
                                ApplicationError.of("ECOSYSTEM_PARTY_NOT_FOUND",
                                        "The referenced Party does not exist")));
                    }
                    if (party.get().kind() != PartyKind.ORGANIZATION) {
                        return Uni.createFrom().item(Result.<RegisterOrganizationResult>failure(
                                ApplicationError.of("ECOSYSTEM_PARTY_KIND_MISMATCH",
                                        "An organization profile requires a Party of kind ORGANIZATION")));
                    }
                    if (!party.get().isActive()) {
                        return Uni.createFrom().item(Result.<RegisterOrganizationResult>failure(
                                ApplicationError.of("ECOSYSTEM_PARTY_INACTIVE",
                                        "An inactive Party cannot receive a new organization profile")));
                    }
                    return transaction.organizations().findByPartyId(command.partyId()).flatMap(existing -> {
                        if (existing.isPresent()) {
                            return Uni.createFrom().item(Result.<RegisterOrganizationResult>failure(
                                    ApplicationError.of("ECOSYSTEM_ORGANIZATION_ALREADY_REGISTERED",
                                            "An organization profile already exists for this Party")));
                        }
                        Organization organization = Organization.register(
                                command.partyId(), command.legalName(), command.displayName(),
                                command.type(), command.tenantId());
                        var events = organization.pullDomainEvents();
                        return transaction.organizations().save(organization)
                                .flatMap(saved -> transaction.events().publish(events)
                                        .replaceWith(Result.success(new RegisterOrganizationResult(
                                                saved.partyId(), saved.status()))));
                    });
                }));
    }
}
