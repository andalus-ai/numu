package tech.kayys.numou.education.pesantren.domain;

import tech.kayys.numou.education.pesantren.domain.event.WalletDeposited;
import tech.kayys.numou.education.pesantren.domain.event.WalletSpent;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A santri's uang saku (pocket-money) wallet held by the pesantren. The balance never goes negative
 * and an optional daily spending limit protects against overspending.
 */
public final class SantriWallet extends AbstractAggregateRoot<SantriWalletId> {

    private final TenantId tenantId;
    private final SantriId santriId;
    private final BigDecimal dailyLimit;
    private final List<WalletTransaction> transactions = new ArrayList<>();
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);

    private SantriWallet(SantriWalletId id, TenantId tenantId, SantriId santriId, BigDecimal dailyLimit) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.santriId = Objects.requireNonNull(santriId, "santriId must not be null");
        if (dailyLimit != null && dailyLimit.signum() <= 0) throw new IllegalArgumentException("dailyLimit must be positive");
        this.dailyLimit = dailyLimit == null ? null : dailyLimit.setScale(2, RoundingMode.HALF_UP);
    }

    /** {@code dailyLimit} may be null for no limit. */
    public static SantriWallet open(SantriWalletId id, TenantId tenantId, SantriId santriId, BigDecimal dailyLimit) {
        return new SantriWallet(id, tenantId, santriId, dailyLimit);
    }

    public void deposit(BigDecimal amount, LocalDate date, String note) {
        BigDecimal a = positive(amount);
        balance = balance.add(a);
        transactions.add(new WalletTransaction(WalletTransaction.Type.DEPOSIT, a, Objects.requireNonNull(date), note));
        touch();
        raise(new WalletDeposited(getId(), tenantId, santriId, a));
    }

    public void spend(BigDecimal amount, LocalDate date, String note) {
        BigDecimal a = positive(amount);
        Objects.requireNonNull(date, "date must not be null");
        if (a.compareTo(balance) > 0) throw new IllegalStateException("Insufficient balance: " + balance.toPlainString());
        if (dailyLimit != null && spentOn(date).add(a).compareTo(dailyLimit) > 0) {
            throw new IllegalStateException("Daily spending limit of " + dailyLimit.toPlainString() + " exceeded");
        }
        balance = balance.subtract(a);
        transactions.add(new WalletTransaction(WalletTransaction.Type.SPEND, a, date, note));
        touch();
        raise(new WalletSpent(getId(), tenantId, santriId, a));
    }

    public BigDecimal spentOn(LocalDate date) {
        return transactions.stream()
                .filter(t -> t.type() == WalletTransaction.Type.SPEND && t.date().equals(date))
                .map(WalletTransaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal positive(BigDecimal v) {
        if (v == null || v.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private void touch() {
        incrementVersion();
        updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public SantriId getSantriId() { return santriId; }
    public BigDecimal getBalance() { return balance; }
    public BigDecimal getDailyLimit() { return dailyLimit; }
    public List<WalletTransaction> getTransactions() { return Collections.unmodifiableList(transactions); }
}
