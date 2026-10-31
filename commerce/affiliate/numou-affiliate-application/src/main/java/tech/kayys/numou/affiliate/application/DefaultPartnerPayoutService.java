package tech.kayys.numou.affiliate.application;

import tech.kayys.numou.affiliate.domain.CommissionEntry;
import tech.kayys.numou.affiliate.domain.CommissionEntryType;
import tech.kayys.numou.affiliate.domain.CommissionFinancePosting;
import tech.kayys.numou.affiliate.domain.CommissionFinancePostingStatus;
import tech.kayys.numou.affiliate.domain.CommissionStatus;
import tech.kayys.numou.affiliate.domain.PartnerPayout;
import tech.kayys.numou.affiliate.domain.PartnerPayoutStatus;
import tech.kayys.numou.affiliate.spi.CommissionEntryRepository;
import tech.kayys.numou.affiliate.spi.CommissionFinanceOutboxRepository;
import tech.kayys.numou.affiliate.spi.PartnerPayoutDestinationPort;
import tech.kayys.numou.affiliate.spi.PartnerPayoutPort;
import tech.kayys.numou.affiliate.spi.PartnerPayoutRepository;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Coordinates affiliate payout intent with the Wallet/Payment public boundary.
 * This service never treats commission approval or liability posting as proof of payment.
 */
public final class DefaultPartnerPayoutService implements PartnerPayoutApplicationPort {
    private final CommissionEntryRepository entries;
    private final CommissionFinanceOutboxRepository financeOutbox;
    private final PartnerPayoutRepository payouts;
    private final PartnerPayoutDestinationPort destinations;
    private final PartnerPayoutPort payoutPort;

    public DefaultPartnerPayoutService(
            CommissionEntryRepository entries,
            CommissionFinanceOutboxRepository financeOutbox,
            PartnerPayoutRepository payouts,
            PartnerPayoutDestinationPort destinations,
            PartnerPayoutPort payoutPort) {
        this.entries = Objects.requireNonNull(entries, "entries is required");
        this.financeOutbox = Objects.requireNonNull(financeOutbox, "financeOutbox is required");
        this.payouts = Objects.requireNonNull(payouts, "payouts is required");
        this.destinations = Objects.requireNonNull(destinations, "destinations is required");
        this.payoutPort = Objects.requireNonNull(payoutPort, "payoutPort is required");
    }

    @Override
    public CompletionStage<PartnerPayout> requestPayout(
            AffiliateCommandContext context, UUID commissionEntryId, String destinationId) {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(commissionEntryId, "commissionEntryId is required");
        String destination = required(destinationId, "destinationId");
        return entries.findById(context.tenantId(), commissionEntryId).thenCompose(found -> {
            if (found.isEmpty()) return failed(new IllegalArgumentException("Commission entry not found for tenant"));
            CommissionEntry entry = found.get();
            if (!context.tenantId().equals(entry.tenantId())) {
                return failed(new IllegalStateException("Commission repository returned a cross-tenant entry"));
            }
            if (entry.status() != CommissionStatus.APPROVED || entry.type() != CommissionEntryType.EARNING
                    || entry.commissionAmount().signum() <= 0) {
                return failed(new IllegalStateException("Only approved positive earning entries are payout-eligible"));
            }
            String financeKey = "affiliate-finance:" + entry.tenantId() + ":" + entry.id();
            return financeOutbox.findByIdempotencyKey(context.tenantId(), financeKey).thenCompose(financePosting -> {
                if (financePosting.isEmpty()
                        || financePosting.get().status() != CommissionFinancePostingStatus.POSTED) {
                    return failed(new IllegalStateException("Commission liability must be posted before payout"));
                }
                CommissionFinancePosting posting = financePosting.get();
                if (!posting.tenantId().equals(entry.tenantId())
                        || !posting.commissionEntryId().equals(entry.id())
                        || posting.amount().compareTo(entry.commissionAmount()) != 0
                        || !posting.currency().equalsIgnoreCase(entry.currency())) {
                    return failed(new IllegalStateException("Posted Finance liability does not match commission entry"));
                }
                return destinations.findVerified(context.tenantId(), entry.partnerId().toString(),
                        destination, entry.currency()).thenCompose(destinationResult -> {
                    if (destinationResult.isEmpty()) {
                        return failed(new IllegalArgumentException("Payout destination not found for tenant and partner"));
                    }
                    PartnerPayoutDestinationPort.VerifiedDestination d = destinationResult.get();
                    if (!context.tenantId().equals(d.tenantId())
                            || !entry.partnerId().toString().equals(d.partnerId())
                            || !destination.equals(d.destinationId())) {
                        return failed(new IllegalStateException("Destination adapter returned a cross-tenant or cross-partner destination"));
                    }
                    if (!d.active() || !d.verified()) {
                        return failed(new IllegalStateException("Payout destination must be active and verified"));
                    }
                    if (!entry.currency().equalsIgnoreCase(d.currency())) {
                        return failed(new IllegalStateException("Payout destination currency does not match commission currency"));
                    }
                    Instant now = Instant.now();
                    String key = "affiliate-payout:" + context.tenantId() + ":" + entry.id();
                    PartnerPayout proposed = new PartnerPayout(UUID.randomUUID(), context.tenantId(),
                            entry.partnerId(), entry.id(), destination, entry.currency(), entry.commissionAmount(),
                            key, context.correlationId(), PartnerPayoutStatus.REQUESTED, 0, null, null, now, now);
                    return payouts.saveOrGet(proposed);
                });
            });
        });
    }

    @Override
    public CompletionStage<PartnerPayout> submitPayout(AffiliateCommandContext context, UUID payoutId) {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(payoutId, "payoutId is required");
        return findPayout(context, payoutId).thenCompose(payout -> {
            if (payout.status() == PartnerPayoutStatus.PAID) return CompletableFuture.completedFuture(payout);
            if (payout.status() != PartnerPayoutStatus.REQUESTED
                    && payout.status() != PartnerPayoutStatus.RETRYABLE_FAILURE) {
                return failed(new IllegalStateException("Payout is not in a submittable state: " + payout.status()));
            }
            return payoutPort.submit(payout).thenCompose(receipt -> applyReceipt(context, payout, receipt))
                    .exceptionallyCompose(error -> payouts.update(context.tenantId(), payout.id(),
                            payout.reconciliationRequired(safeMessage(error), Instant.now()), context.actorId()));
        });
    }

    @Override
    public CompletionStage<PartnerPayout> reconcilePayout(AffiliateCommandContext context, UUID payoutId) {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(payoutId, "payoutId is required");
        return findPayout(context, payoutId).thenCompose(payout -> {
            if (payout.status() == PartnerPayoutStatus.PAID) return CompletableFuture.completedFuture(payout);
            if (payout.status() != PartnerPayoutStatus.RECONCILIATION_REQUIRED
                    && payout.status() != PartnerPayoutStatus.SUBMITTED) {
                return failed(new IllegalStateException("Payout is not awaiting reconciliation"));
            }
            return payoutPort.findByIdempotencyKey(context.tenantId(), payout.idempotencyKey())
                    .thenCompose(receipt -> {
                        if (receipt.isEmpty()) return CompletableFuture.completedFuture(payout);
                        return applyReceipt(context, payout, receipt.get());
                    });
        });
    }

    private CompletionStage<PartnerPayout> applyReceipt(
            AffiliateCommandContext context, PartnerPayout payout, PartnerPayoutPort.PayoutReceipt receipt) {
        if (receipt == null || !context.tenantId().equals(receipt.tenantId())
                || !payout.idempotencyKey().equals(receipt.idempotencyKey())
                || !payout.currency().equalsIgnoreCase(receipt.currency())
                || payout.amount().compareTo(receipt.amount()) != 0) {
            return payouts.update(context.tenantId(), payout.id(),
                    payout.reconciliationRequired("Payout receipt did not match tenant, key, currency, or amount",
                            Instant.now()), context.actorId());
        }
        Instant at = receipt.occurredAt();
        PartnerPayout updated = switch (receipt.outcome()) {
            case ACCEPTED -> payout.status() == PartnerPayoutStatus.SUBMITTED
                    || payout.status() == PartnerPayoutStatus.RECONCILIATION_REQUIRED ? payout
                    : payout.submitted(receipt.providerReference(), at);
            case CONFIRMED -> payout.paid(receipt.providerReference(), at);
            case RETURNED -> payout.returned(receipt.providerReference(), "Provider reported returned payout", at);
            case PENDING_RECONCILIATION -> payout.reconciliationRequired(
                    "Provider outcome remains ambiguous", at);
        };
        return payouts.update(context.tenantId(), payout.id(), updated, context.actorId());
    }

    private CompletionStage<PartnerPayout> findPayout(AffiliateCommandContext context, UUID payoutId) {
        return payouts.findById(context.tenantId(), payoutId).thenCompose(found -> {
            if (found.isEmpty()) return failed(new IllegalArgumentException("Payout not found for tenant"));
            if (!context.tenantId().equals(found.get().tenantId())) {
                return failed(new IllegalStateException("Payout repository returned a cross-tenant payout"));
            }
            return CompletableFuture.completedFuture(found.get());
        });
    }

    private static String safeMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        String message = root.getMessage();
        if (message == null || message.isBlank()) message = root.getClass().getSimpleName();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }

    private static <T> CompletionStage<T> failed(Throwable error) {
        return CompletableFuture.failedFuture(error);
    }
}
