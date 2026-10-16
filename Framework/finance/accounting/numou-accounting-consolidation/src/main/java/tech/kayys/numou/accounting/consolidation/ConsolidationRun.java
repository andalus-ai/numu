package tech.kayys.syirkah.accounting.consolidation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Aggregate controlling one immutable reporting-period consolidation run. */
public final class ConsolidationRun {
    public enum Status { DRAFT, COLLECTING, ELIMINATING, TRANSLATING, CLOSED, PUBLISHED, REOPENED }
    public enum EliminationKind { RECIPROCAL_BALANCE, INTERCOMPANY_PROFIT, DIVIDEND, CUSTOM }

    public record Elimination(String sourceCompany, String targetCompany, EliminationKind kind,
                              BigDecimal amount, String currency, String reference) {
        public Elimination {
            requireText(sourceCompany, "sourceCompany");
            requireText(targetCompany, "targetCompany");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(amount, "amount");
            if (amount.signum() < 0) throw new IllegalArgumentException("amount must not be negative");
            requireText(currency, "currency");
            requireText(reference, "reference");
        }
    }

    private final UUID id;
    private final String tenantId;
    private final LocalDate periodEnd;
    private final String presentationCurrency;
    private final Map<String, BigDecimal> trialBalance = new LinkedHashMap<>();
    private final List<Elimination> eliminations = new ArrayList<>();
    private Status status = Status.DRAFT;
    private Instant updatedAt = Instant.now();

    public ConsolidationRun(UUID id, String tenantId, LocalDate periodEnd, String presentationCurrency) {
        this.id = Objects.requireNonNull(id, "id");
        requireText(tenantId, "tenantId");
        this.tenantId = tenantId;
        this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd");
        requireText(presentationCurrency, "presentationCurrency");
        this.presentationCurrency = presentationCurrency.toUpperCase(Locale.ROOT);
    }

    public void collect(String account, BigDecimal amount) {
        if (status == Status.DRAFT) {
            status = Status.COLLECTING;
        } else if (status != Status.COLLECTING) {
            throw new IllegalStateException("Expected DRAFT or COLLECTING but was " + status);
        }
        requireText(account, "account");
        trialBalance.merge(account, Objects.requireNonNull(amount, "amount"), BigDecimal::add);
        updatedAt = Instant.now();
    }

    public void eliminate(Elimination elimination) {
        if (status == Status.COLLECTING) {
            status = Status.ELIMINATING;
        } else if (status != Status.ELIMINATING) {
            throw new IllegalStateException("Expected COLLECTING or ELIMINATING but was " + status);
        }
        eliminations.add(Objects.requireNonNull(elimination, "elimination"));
        updatedAt = Instant.now();
    }

    public void translate() {
        if (status != Status.ELIMINATING && status != Status.COLLECTING) {
            throw new IllegalStateException("Run must be collecting or eliminating before translation");
        }
        status = Status.TRANSLATING;
        updatedAt = Instant.now();
    }

    public void close() {
        if (status != Status.TRANSLATING) throw new IllegalStateException("Run must be translated before closing");
        status = Status.CLOSED;
        updatedAt = Instant.now();
    }

    public void publish() {
        if (status != Status.CLOSED) throw new IllegalStateException("Run must be closed before publishing");
        status = Status.PUBLISHED;
        updatedAt = Instant.now();
    }

    public UUID id() { return id; }
    public String tenantId() { return tenantId; }
    public LocalDate periodEnd() { return periodEnd; }
    public String presentationCurrency() { return presentationCurrency; }
    public Status status() { return status; }
    public Instant updatedAt() { return updatedAt; }
    public Map<String, BigDecimal> trialBalance() { return Map.copyOf(trialBalance); }
    public List<Elimination> eliminations() { return List.copyOf(eliminations); }

    private void transition(Status expected, Status next) {
        if (status != expected) throw new IllegalStateException("Expected " + expected + " but was " + status);
        status = next;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
