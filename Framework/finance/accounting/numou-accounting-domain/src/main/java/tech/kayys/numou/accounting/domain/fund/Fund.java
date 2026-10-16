package tech.kayys.syirkah.accounting.domain.fund;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An accounting entity with a self-balancing set of accounts segregated for specific activities.
 */
public final class Fund {
    private final FundId id;
    private final String code;
    private final String name;
    private final FundType type;
    private BigDecimal balance;

    public Fund(FundId id, String code, String name, FundType type, BigDecimal initialBalance) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.balance = initialBalance != null ? initialBalance : BigDecimal.ZERO;
    }

    public void credit(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() < 0) throw new IllegalArgumentException("Credit amount must be positive");
        this.balance = this.balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() < 0) throw new IllegalArgumentException("Debit amount must be positive");
        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient fund balance in fund " + code + ": available " + balance + ", required " + amount);
        }
        this.balance = this.balance.subtract(amount);
    }

    public FundId id() { return id; }
    public String code() { return code; }
    public String name() { return name; }
    public FundType type() { return type; }
    public BigDecimal balance() { return balance; }
}
