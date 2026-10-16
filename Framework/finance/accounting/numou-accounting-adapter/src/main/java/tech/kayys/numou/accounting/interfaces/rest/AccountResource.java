package tech.kayys.syirkah.accounting.interfaces.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.syirkah.accounting.application.api.command.CreateAccountCommand;
import tech.kayys.syirkah.accounting.application.cqrs.CommandBus;
import tech.kayys.syirkah.accounting.application.port.AccountRepository;
import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.model.Account;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.accounting.domain.valueobject.AccountType;

import java.util.List;
import java.util.UUID;

@Path("/api/v1/accounting/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Chart of Accounts API", description = "Endpoints for managing Accounts and COA hierarchy")
public class AccountResource {

    @Inject
    AccountRepository accountRepository;

    @Inject
    CommandBus commandBus;

    
public record CreateAccountRequest(
            String tenantId,
            String ledgerId,
            String accountNumber,
            String name,
            String description,
            String accountType,
            String currencyCode
    ) {}

    @GET
    @Operation(summary = "List all accounts in Chart of Accounts")
    public Uni<List<Account>> listAccounts() {
        return accountRepository.findAll();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get account details by ID")
    public Uni<Response> getAccount(@PathParam("id") UUID id) {
        return accountRepository.findById(AccountId.of(id))
                .map(opt -> opt.map(a -> Response.ok(a).build())
                               .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build()));
    }

    @POST
    @Operation(summary = "Create a new Account via CQRS Command Bus")
    public Uni<Response> createAccount(CreateAccountRequest req) {
        TenantRef tId = req.tenantId() != null ? TenantRef.of(req.tenantId()) : TenantRef.defaultTenant();
        LedgerId lId = req.ledgerId() != null ? LedgerId.of(req.ledgerId()) : LedgerId.primary();
        AccountType type = AccountType.valueOf(req.accountType().toUpperCase());

        CreateAccountCommand cmd = new CreateAccountCommand(tId, lId, req.accountNumber(), req.name(), req.description(), type, req.currencyCode());
        return commandBus.<CreateAccountCommand, AccountId>dispatch(cmd)
                .map(newId -> Response.status(Response.Status.CREATED).entity(newId).build());
    }

    @PUT
    @Path("/{id}/deactivate")
    @Operation(summary = "Deactivate an account")
    public Uni<Response> deactivateAccount(@PathParam("id") UUID id) {
        return accountRepository.findById(AccountId.of(id))
                .chain(opt -> {
                    if (opt.isEmpty()) {
                        return Uni.createFrom().item(Response.status(Response.Status.NOT_FOUND).build());
                    }
                    Account acc = opt.get();
                    acc.deactivate();
                    return accountRepository.save(acc)
                            .map(v -> Response.ok(acc).build());
                });
    }
}
