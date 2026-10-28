package tech.kayys.numou.mobility.api;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import tech.kayys.numou.wallet.application.EarningsStatement;
import tech.kayys.numou.wallet.application.PayoutService;
import tech.kayys.numou.wallet.application.TopUpService;
import tech.kayys.numou.wallet.application.WalletService;
import tech.kayys.numou.wallet.application.WalletView;
import tech.kayys.numou.wallet.domain.LedgerEntry;
import tech.kayys.numou.wallet.domain.OwnerKind;
import tech.kayys.numou.security.adapter.Secured;
import tech.kayys.numou.security.domain.valueobject.ActionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static tech.kayys.numou.security.application.authz.RolePermissionMatrix.Resources.WALLET;
import static tech.kayys.numou.security.application.authz.RolePermissionMatrix.Resources.WALLET_PAYOUT;
import static tech.kayys.numou.security.application.authz.RolePermissionMatrix.Resources.WALLET_TOPUP;

/**
 * Wallets, top-ups, payouts and statements. A caller can only ever act on their own wallet (the owner is the token's user id). Money enters a
 * wallet only through a verified provider callback ({@link WebhookResource}) or the gateway service account. NOT COMPILED in the authoring sandbox.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WalletResource {

    public record PayoutRequest(@NotNull @DecimalMin("1") BigDecimal amount, @NotBlank @Size(max = 120) String bankAccountRef) { }

    public record TopUpRequest(@NotNull @DecimalMin("1") BigDecimal amount, @NotBlank @Size(max = 30) String provider) { }

    public record GatewayTopUpRequest(@NotNull UUID userId, @NotNull @DecimalMin("1") BigDecimal amount, @NotBlank @Size(max = 100) String gatewayRef) { }

    @Inject WalletService wallets;
    @Inject TopUpService topUps;
    @Inject PayoutService payouts;
    @Inject Secured secured;

    @GET
    @Path("/api/v1/wallet/rider")
    public Uni<WalletView> rider() {
        return secured.run("wallet.rider", ActionType.READ, WALLET, ctx -> wallets.view(ctx.tenantId(), OwnerKind.RIDER, Actors.id(ctx)));
    }

    @GET
    @Path("/api/v1/wallet/driver")
    public Uni<WalletView> driver() {
        return secured.run("wallet.driver", ActionType.READ, WALLET, ctx -> wallets.view(ctx.tenantId(), OwnerKind.DRIVER, Actors.id(ctx)));
    }

    @GET
    @Path("/api/v1/wallet/{kind}/history")
    public Uni<List<LedgerEntry>> history(@PathParam("kind") String kind, @QueryParam("limit") @DefaultValue("50") @Min(1) @Max(200) int limit) {
        final OwnerKind k = "driver".equalsIgnoreCase(kind) ? OwnerKind.DRIVER : OwnerKind.RIDER;       // PLATFORM wallets are never exposed here
        return secured.run("wallet.history", ActionType.READ, WALLET, ctx -> wallets.history(ctx.tenantId(), k, Actors.id(ctx), limit));
    }

    /** Earnings for a period (at most a year): ride earnings, cash commission owed, tips, fees, payouts, adjustments. */
    @GET
    @Path("/api/v1/wallet/driver/statement")
    public Uni<EarningsStatement> statement(@QueryParam("from") @NotNull Instant from, @QueryParam("to") @NotNull Instant to) {
        return secured.run("wallet.statement", ActionType.READ, WALLET, ctx -> wallets.statement(ctx.tenantId(), OwnerKind.DRIVER, Actors.id(ctx), from, to));
    }

    /** Starts a top-up: returns the order reference and the provider's payment page. The wallet is credited later, by the verified callback. */
    @POST
    @Path("/api/v1/wallet/topups")
    public Uni<TopUpService.TopUpView> topUp(@Valid TopUpRequest r) {
        return secured.run("wallet.topup.create", ActionType.CREATE, WALLET, ctx -> topUps.create(ctx.tenantId(), Actors.id(ctx), r.amount(), r.provider()));
    }

    /** Withdraw to a bank account. The idempotency key makes a retried request withdraw once. */
    @POST
    @Path("/api/v1/wallet/driver/payout")
    public Uni<PayoutService.PayoutView> payout(@HeaderParam("Idempotency-Key") @NotBlank @Size(max = 100) String key, @Valid PayoutRequest r) {
        return secured.run("wallet.payout", ActionType.PERFORM, WALLET_PAYOUT,
                ctx -> payouts.request(ctx.tenantId(), Actors.id(ctx), r.amount(), r.bankAccountRef(), key));
    }

    /** Service-to-service credit from the payment-gateway account (kept for gateways that cannot use signed callbacks). */
    @POST
    @Path("/api/v1/internal/wallet/topup")
    public Uni<WalletView> gatewayTopUp(@Valid GatewayTopUpRequest r) {
        return secured.run("wallet.topup", ActionType.PERFORM, WALLET_TOPUP,
                ctx -> wallets.credit(ctx.tenantId(), OwnerKind.RIDER, r.userId(), r.amount(), "topup:" + r.gatewayRef(), "top-up"));
    }
}
