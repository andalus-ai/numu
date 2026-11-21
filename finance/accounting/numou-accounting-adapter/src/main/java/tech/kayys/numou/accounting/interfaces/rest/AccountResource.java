package tech.kayys.numou.accounting.interfaces.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.accounting.application.api.command.CreateAccountCommand;
import tech.kayys.numou.accounting.application.cqrs.CommandBus;
import tech.kayys.numou.accounting.application.port.AccountRepository;
import tech.kayys.numou.accounting.domain.identifier.AccountId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.Account;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;
import tech.kayys.numou.accounting.domain.valueobject.AccountType;

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
    @Operation(summary = "List accounts in the specified tenant and ledger")
    public Uni<List<Account>> listAccounts(
            @QueryParam("tenantId") String tenantId,
            @QueryParam("ledgerId") String ledgerId) {
        Scope scope = requireScope(tenantId, ledgerId);
        return accountRepository.findAll(scope.tenantId(), scope.ledgerId());
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get account details by ID within a tenant and ledger")
    public Uni<Response> getAccount(
            @PathParam("id") UUID id,
            @QueryParam("tenantId") String tenantId,
            @QueryParam("ledgerId") String ledgerId) {
        Scope scope = requireScope(tenantId, ledgerId);
        return accountRepository.findById(scope.tenantId(), scope.ledgerId(), AccountId.of(id))
                .map(opt -> opt.map(a -> Response.ok(a).build())
                        .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build()));
    }

    @POST
    @Operation(summary = "Create a new Account via CQRS Command Bus")
    public Uni<Response> createAccount(CreateAccountRequest req) {
        if (req == null) {
            throw new BadRequestException("Request body is required");
        }
        TenantRef tenantId = req.tenantId() != null ? TenantRef.of(req.tenantId()) : TenantRef.defaultTenant();
        LedgerId ledgerId = req.ledgerId() != null ? LedgerId.of(req.ledgerId()) : LedgerId.primary();
        AccountType type = AccountType.valueOf(req.accountType().toUpperCase());

        CreateAccountCommand cmd = new CreateAccountCommand(
                tenantId, ledgerId, req.accountNumber(), req.name(), req.description(), type, req.currencyCode());
        return commandBus.<CreateAccountCommand, AccountId>dispatch(cmd)
                .map(newId -> Response.status(Response.Status.CREATED).entity(newId).build());
    }

    @PUT
    @Path("/{id}/deactivate")
    @Operation(summary = "Deactivate an account within a tenant and ledger")
    public Uni<Response> deactivateAccount(
            @PathParam("id") UUID id,
            @QueryParam("tenantId") String tenantId,
            @QueryParam("ledgerId") String ledgerId) {
        Scope scope = requireScope(tenantId, ledgerId);
        return accountRepository.findById(scope.tenantId(), scope.ledgerId(), AccountId.of(id))
                .chain(opt -> {
                    if (opt.isEmpty()) {
                        return Uni.createFrom().item(Response.status(Response.Status.NOT_FOUND).build());
                    }
                    Account account = opt.get();
                    account.deactivate();
                    return accountRepository.save(account).map(v -> Response.ok(account).build());
                });
    }

    private static Scope requireScope(String tenantId, String ledgerId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new BadRequestException("tenantId query parameter is required");
        }
        if (ledgerId == null || ledgerId.isBlank()) {
            throw new BadRequestException("ledgerId query parameter is required");
        }
        return new Scope(TenantRef.of(tenantId), LedgerId.of(ledgerId));
    }

    private record Scope(TenantRef tenantId, LedgerId ledgerId) {}
}
