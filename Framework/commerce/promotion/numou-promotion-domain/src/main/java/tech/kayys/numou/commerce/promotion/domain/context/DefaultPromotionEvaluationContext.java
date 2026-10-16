package tech.kayys.syirkah.commerce.promotion.domain.context;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable default {@link PromotionEvaluationContext}.
 *
 * <p>The 6-argument constructor keeps the legacy callers (tests and the v1
 * cart bridge) working; it defaults {@code tenantId} to a synthetic tenant
 * and {@code branchId} to empty. New callers should use the 8-argument
 * constructor to make tenant scope explicit (product04.md section 21).</p>
 */
public final class DefaultPromotionEvaluationContext
        implements PromotionEvaluationContext {

    private final Instant effectiveAt;
    private final ChannelId channel;
    private final Optional<CustomerSnapshot> customer;
    private final CartSnapshot cart;
    private final List<PromotionLineSnapshot> lines;
    private final Optional<PromotionLineId> currentLineId;
    private final TenantRef tenantId;
    private final Optional<BranchId> branchId;

    public DefaultPromotionEvaluationContext(
            Instant effectiveAt,
            ChannelId channel,
            Optional<CustomerSnapshot> customer,
            CartSnapshot cart,
            List<PromotionLineSnapshot> lines,
            Optional<PromotionLineId> currentLineId
    ) {
        this(effectiveAt, channel, customer, cart, lines, currentLineId,
                TenantRef.of("DEFAULT"), Optional.empty());
    }

    public DefaultPromotionEvaluationContext(
            Instant effectiveAt,
            ChannelId channel,
            Optional<CustomerSnapshot> customer,
            CartSnapshot cart,
            List<PromotionLineSnapshot> lines,
            Optional<PromotionLineId> currentLineId,
            TenantRef tenantId,
            Optional<BranchId> branchId
    ) {
        this.effectiveAt = Objects.requireNonNull(effectiveAt, "effectiveAt cannot be null");
        this.channel = Objects.requireNonNull(channel, "channel cannot be null");
        this.customer = Objects.requireNonNull(customer, "customer cannot be null");
        this.cart = Objects.requireNonNull(cart, "cart cannot be null");
        Objects.requireNonNull(lines, "lines cannot be null");
        this.lines = List.copyOf(lines);
        this.currentLineId = Objects.requireNonNull(currentLineId, "currentLineId cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.branchId = Objects.requireNonNull(branchId, "branchId cannot be null");
    }

    /** Empty context for stacking overloads that ignore context detail. */
    public static DefaultPromotionEvaluationContext empty() {
        Money zero = Money.zero("IDR");
        return new DefaultPromotionEvaluationContext(
                Instant.EPOCH,
                ChannelId.of("UNKNOWN"),
                Optional.empty(),
                new CartSnapshot(zero, zero, zero),
                List.of(),
                Optional.empty());
    }

    @Override
    public Instant effectiveAt() {
        return effectiveAt;
    }

    @Override
    public ChannelId channel() {
        return channel;
    }

    @Override
    public Optional<CustomerSnapshot> customer() {
        return customer;
    }

    @Override
    public CartSnapshot cart() {
        return cart;
    }

    @Override
    public List<PromotionLineSnapshot> lines() {
        return lines;
    }

    @Override
    public Optional<PromotionLineId> currentLineId() {
        return currentLineId;
    }

    @Override
    public TenantRef tenantId() {
        return tenantId;
    }

    @Override
    public Optional<BranchId> branchId() {
        return branchId;
    }
}
