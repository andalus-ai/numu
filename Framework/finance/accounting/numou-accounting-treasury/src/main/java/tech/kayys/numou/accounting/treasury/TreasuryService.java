package tech.kayys.syirkah.accounting.treasury;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/**
 * In-memory treasury application boundary.  Domain records model the
 * principal treasury plan surfaces while the interfaces are adapter ports for
 * banks, payment formats, forecasting sources, and durable projections.
 */
public final class TreasuryService {
    public enum AccountStatus { ACTIVE, SUSPENDED, CLOSED }
    public enum BatchStatus { DRAFT, APPROVED, SUBMITTED, SETTLED, FAILED }
    public enum ForecastMethod { DIRECT, INDIRECT }
    public enum NettingStatus { DRAFT, READY, EXECUTED }
    public enum HedgeType { FORWARD, OPTION, SWAP }
    public enum PoolingType { ZERO_BALANCE, NOTIONAL }
    public enum DebtStatus { ACTIVE, MATURED }
    public enum InvestmentStatus { ACTIVE, MATURED, REDEEMED }

    public record BankAccount(UUID id, String tenantId, String currency, String maskedNumber, AccountStatus status) {
        public BankAccount {
            Objects.requireNonNull(id); text(tenantId, "tenantId"); currency = upper(currency);
            text(maskedNumber, "maskedNumber"); Objects.requireNonNull(status);
        }
    }
    public record Payment(UUID id, UUID bankAccountId, BigDecimal amount, String currency,
                          String beneficiary, String reference) {
        public Payment {
            Objects.requireNonNull(id); Objects.requireNonNull(bankAccountId);
            Objects.requireNonNull(amount); if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
            currency = upper(currency); text(beneficiary, "beneficiary"); text(reference, "reference");
        }
    }
    public record PaymentInstruction(UUID id, UUID bankAccountId, BigDecimal amount,
                                     String currency, String beneficiary, String reference) {
        public PaymentInstruction {
            Objects.requireNonNull(id); Objects.requireNonNull(bankAccountId);
            Objects.requireNonNull(amount); if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
            currency = upper(currency); text(beneficiary, "beneficiary"); text(reference, "reference");
        }
        Payment payment() { return new Payment(id, bankAccountId, amount, currency, beneficiary, reference); }
    }
    public record PaymentBatch(UUID id, String tenantId, List<Payment> payments,
                               BatchStatus status, Instant updatedAt) {
        public PaymentBatch {
            Objects.requireNonNull(id); text(tenantId, "tenantId"); payments = List.copyOf(payments);
            Objects.requireNonNull(status); Objects.requireNonNull(updatedAt);
        }
        public BigDecimal total() { return payments.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add); }
    }

    public record CashPosition(UUID accountId, String currency, BigDecimal bankBalance,
                               BigDecimal bookBalance, BigDecimal availableBalance, Instant asOf) {
        public CashPosition {
            Objects.requireNonNull(accountId); currency = upper(currency);
            Objects.requireNonNull(bankBalance); Objects.requireNonNull(bookBalance);
            Objects.requireNonNull(availableBalance); Objects.requireNonNull(asOf);
        }
        public BigDecimal variance() { return bankBalance.subtract(bookBalance); }
    }
    public record ForecastLine(LocalDate start, LocalDate end, String category,
                               BigDecimal amount, String currency) {
        public ForecastLine {
            Objects.requireNonNull(start); Objects.requireNonNull(end); text(category, "category");
            Objects.requireNonNull(amount); currency = upper(currency);
            if (end.isBefore(start)) throw new IllegalArgumentException("end before start");
        }
    }
    public record CashForecast(UUID id, ForecastMethod method, LocalDate asOf,
                               List<ForecastLine> lines) {
        public CashForecast {
            Objects.requireNonNull(id); Objects.requireNonNull(method); Objects.requireNonNull(asOf);
            lines = List.copyOf(lines);
        }
        public BigDecimal net() {
            return lines.stream().map(ForecastLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
    public record LiquidityPlan(UUID id, String currency, BigDecimal minimum,
                                BigDecimal target, BigDecimal maximum, UUID sweepAccountId) {
        public LiquidityPlan {
            Objects.requireNonNull(id); currency = upper(currency);
            Objects.requireNonNull(minimum); Objects.requireNonNull(target); Objects.requireNonNull(maximum);
            Objects.requireNonNull(sweepAccountId);
            if (minimum.compareTo(target) > 0 || target.compareTo(maximum) > 0)
                throw new IllegalArgumentException("minimum <= target <= maximum is required");
        }
        public BigDecimal sweep(BigDecimal balance) {
            Objects.requireNonNull(balance);
            return balance.compareTo(target) < 0 ? target.subtract(balance) : balance.subtract(target).negate();
        }
    }
    public record NettingPosition(String companyId, String currency,
                                  BigDecimal receivable, BigDecimal payable) {
        public NettingPosition {
            text(companyId, "companyId"); currency = upper(currency);
            Objects.requireNonNull(receivable); Objects.requireNonNull(payable);
        }
        public BigDecimal net() { return receivable.subtract(payable); }
    }
    public record NettingResult(List<NettingPosition> positions, BigDecimal residual) {
        public NettingResult { positions = List.copyOf(positions); Objects.requireNonNull(residual); }
    }
    public record NettingCycle(UUID id, String currency, List<NettingPosition> positions,
                               NettingStatus status, Instant updatedAt) {
        public NettingCycle {
            Objects.requireNonNull(id); currency = upper(currency); positions = List.copyOf(positions);
            Objects.requireNonNull(status); Objects.requireNonNull(updatedAt);
        }
    }
    public record FxExposure(UUID id, String fromCurrency, String toCurrency,
                             BigDecimal amount, LocalDate maturity, BigDecimal hedgedAmount) {
        public FxExposure {
            Objects.requireNonNull(id); fromCurrency = upper(fromCurrency); toCurrency = upper(toCurrency);
            Objects.requireNonNull(amount); Objects.requireNonNull(maturity); Objects.requireNonNull(hedgedAmount);
            if (amount.signum() <= 0 || hedgedAmount.signum() < 0 || hedgedAmount.compareTo(amount) > 0)
                throw new IllegalArgumentException("invalid exposure or hedge amount");
        }
        public BigDecimal unhedgedAmount() { return amount.subtract(hedgedAmount); }
    }
    public record HedgeDesignation(UUID id, UUID exposureId, HedgeType type,
                                   BigDecimal notional, BigDecimal rate, Instant designatedAt) {
        public HedgeDesignation {
            Objects.requireNonNull(id); Objects.requireNonNull(exposureId); Objects.requireNonNull(type);
            Objects.requireNonNull(notional); Objects.requireNonNull(rate); Objects.requireNonNull(designatedAt);
            if (notional.signum() <= 0 || rate.signum() <= 0) throw new IllegalArgumentException("notional/rate must be positive");
        }
    }
    public record HedgeEffectiveness(UUID designationId, BigDecimal ratio, boolean effective, Instant testedAt) {}
    public record CashPool(UUID id, String currency, PoolingType type,
                           UUID headerAccountId, List<UUID> participants) {
        public CashPool {
            Objects.requireNonNull(id); currency = upper(currency); Objects.requireNonNull(type);
            Objects.requireNonNull(headerAccountId); participants = List.copyOf(participants);
            if (participants.contains(headerAccountId)) throw new IllegalArgumentException("header cannot participate");
        }
    }
    public record DebtFacility(UUID id, String currency, BigDecimal limit,
                               BigDecimal drawn, BigDecimal annualRate, LocalDate maturity,
                               BigDecimal accruedInterest, DebtStatus status) {
        public DebtFacility {
            Objects.requireNonNull(id); currency = upper(currency); Objects.requireNonNull(limit);
            Objects.requireNonNull(drawn); Objects.requireNonNull(annualRate); Objects.requireNonNull(maturity);
            Objects.requireNonNull(accruedInterest); Objects.requireNonNull(status);
        }
        public BigDecimal available() { return limit.subtract(drawn); }
    }
    public record InvestmentPosition(UUID id, String currency, BigDecimal principal,
                                    BigDecimal rate, LocalDate maturity, InvestmentStatus status) {
        public InvestmentPosition {
            Objects.requireNonNull(id); currency = upper(currency); Objects.requireNonNull(principal);
            Objects.requireNonNull(rate); Objects.requireNonNull(maturity); Objects.requireNonNull(status);
            if (principal.signum() <= 0 || rate.signum() < 0) throw new IllegalArgumentException("invalid investment");
        }
    }

    public record BankStatementLine(String accountNumber, LocalDate valueDate,
                                    String reference, BigDecimal amount, String currency) {
        public BankStatementLine {
            text(accountNumber, "accountNumber"); Objects.requireNonNull(valueDate);
            text(reference, "reference"); Objects.requireNonNull(amount); currency = upper(currency);
        }
    }
    public record BankStatementImport(String accountNumber, List<BankStatementLine> lines) {
        public BankStatementImport { text(accountNumber, "accountNumber"); lines = List.copyOf(lines); }
    }
    public interface BankConnectivity {
        String code();
        BankStatementImport importStatement(BankAccount account);
    }
    public static final class InMemoryBankConnectivity implements BankConnectivity {
        private final Map<String, List<BankStatementLine>> statements = new HashMap<>();
        public void register(String accountNumber, List<BankStatementLine> lines) {
            statements.put(text(accountNumber, "accountNumber"), List.copyOf(lines));
        }
        public String code() { return "IN_MEMORY"; }
        public BankStatementImport importStatement(BankAccount account) {
            Objects.requireNonNull(account);
            return new BankStatementImport(account.maskedNumber(), statements.getOrDefault(account.maskedNumber(), List.of()));
        }
    }
    public enum PaymentFileFormat { ISO20022_PAIN_001, CSV, LOCAL }
    public interface PaymentFileGenerator {
        PaymentFileFormat format();
        String generate(PaymentBatch batch);
    }
    public static final class CsvPaymentFileGenerator implements PaymentFileGenerator {
        public PaymentFileFormat format() { return PaymentFileFormat.CSV; }
        public String generate(PaymentBatch batch) {
            var builder = new StringBuilder("beneficiary,amount,currency,reference\n");
            for (var payment : batch.payments()) {
                builder.append(payment.beneficiary()).append(',')
                        .append(payment.amount()).append(',')
                        .append(payment.currency()).append(',')
                        .append(payment.reference()).append('\n');
            }
            return builder.toString();
        }
    }
    public static final class Iso20022PaymentFileGenerator implements PaymentFileGenerator {
        public PaymentFileFormat format() { return PaymentFileFormat.ISO20022_PAIN_001; }
        public String generate(PaymentBatch batch) {
            var builder = new StringBuilder("<Document><CstmrCdtTrfInitn><GrpHdr><NbOfTxs>")
                    .append(batch.payments().size()).append("</NbOfTxs><CtrlSum>")
                    .append(batch.total()).append("</CtrlSum></GrpHdr>");
            for (var payment : batch.payments()) {
                builder.append("<CdtTrfTxInf><EndToEndId>").append(payment.reference())
                        .append("</EndToEndId><InstdAmt Ccy=\"").append(payment.currency())
                        .append("\">").append(payment.amount()).append("</InstdAmt></CdtTrfTxInf>");
            }
            return builder.append("</CstmrCdtTrfInitn></Document>").toString();
        }
    }
    public interface ForecastEngine {
        ForecastMethod method();
        List<ForecastLine> forecast(LocalDate asOf, int periods, String currency);
    }
    public interface TreasuryRepository<T> {
        void save(T value);
        Optional<T> find(UUID id);
        Collection<T> all();
    }
    public record TreasuryView(List<BankAccount> accounts, List<CashPosition> positions,
                               List<PaymentBatch> batches, Instant generatedAt) {
        public TreasuryView { accounts = List.copyOf(accounts); positions = List.copyOf(positions); batches = List.copyOf(batches); }
    }

    private final Map<UUID, BankAccount> accounts = new LinkedHashMap<>();
    private final Map<UUID, PaymentBatch> batches = new LinkedHashMap<>();
    private final Map<UUID, CashPosition> positions = new LinkedHashMap<>();
    private final Map<UUID, CashForecast> forecasts = new LinkedHashMap<>();
    private final Map<UUID, LiquidityPlan> liquidityPlans = new LinkedHashMap<>();
    private final Map<UUID, FxExposure> exposures = new LinkedHashMap<>();
    private final Map<UUID, HedgeDesignation> designations = new LinkedHashMap<>();
    private final Map<UUID, CashPool> pools = new LinkedHashMap<>();
    private final Map<UUID, DebtFacility> debts = new LinkedHashMap<>();
    private final Map<UUID, InvestmentPosition> investments = new LinkedHashMap<>();
    private final Map<UUID, NettingCycle> nettingCycles = new LinkedHashMap<>();

    public BankAccount registerAccount(String tenantId, String currency, String maskedNumber) {
        var account = new BankAccount(UUID.randomUUID(), tenantId, currency, maskedNumber, AccountStatus.ACTIVE);
        accounts.put(account.id(), account); return account;
    }
    public BankAccount suspendAccount(UUID id) { return updateAccount(id, AccountStatus.SUSPENDED); }
    public BankAccount closeAccount(UUID id) { return updateAccount(id, AccountStatus.CLOSED); }
    private BankAccount updateAccount(UUID id, AccountStatus status) {
        var current = requireAccount(id);
        var updated = new BankAccount(current.id(), current.tenantId(), current.currency(), current.maskedNumber(), status);
        accounts.put(id, updated); return updated;
    }
    public PaymentBatch createBatch(String tenantId, List<Payment> payments) {
        if (payments == null || payments.isEmpty()) throw new IllegalArgumentException("payments must not be empty");
        for (var payment : payments) {
            var account = requireAccount(payment.bankAccountId());
            if (account.status() != AccountStatus.ACTIVE) throw new IllegalStateException("Bank account is not active");
            if (!account.tenantId().equals(tenantId)) throw new IllegalArgumentException("Payment tenant mismatch");
            if (!account.currency().equals(payment.currency())) throw new IllegalArgumentException("Payment currency mismatch");
        }
        var batch = new PaymentBatch(UUID.randomUUID(), tenantId, payments, BatchStatus.DRAFT, Instant.now());
        batches.put(batch.id(), batch); return batch;
    }
    public PaymentBatch createBatchFromInstructions(String tenantId, List<PaymentInstruction> instructions) {
        return createBatch(tenantId, instructions.stream().map(PaymentInstruction::payment).toList());
    }
    public PaymentBatch approve(UUID id) { return transition(id, BatchStatus.DRAFT, BatchStatus.APPROVED); }
    public PaymentBatch submit(UUID id) { return transition(id, BatchStatus.APPROVED, BatchStatus.SUBMITTED); }
    public PaymentBatch settle(UUID id) { return transition(id, BatchStatus.SUBMITTED, BatchStatus.SETTLED); }
    public PaymentBatch fail(UUID id) { return transition(id, BatchStatus.SUBMITTED, BatchStatus.FAILED); }
    public PaymentBatch requireBatch(UUID id) { return Optional.ofNullable(batches.get(id)).orElseThrow(() -> new NoSuchElementException("Unknown payment batch: " + id)); }
    private PaymentBatch transition(UUID id, BatchStatus expected, BatchStatus next) {
        var current = requireBatch(id);
        if (current.status() != expected) throw new IllegalStateException("Expected " + expected + " but was " + current.status());
        var updated = new PaymentBatch(id, current.tenantId(), current.payments(), next, Instant.now());
        batches.put(id, updated); return updated;
    }

    public CashPosition recordCash(UUID accountId, BigDecimal bank, BigDecimal book, BigDecimal available) {
        var account = requireAccount(accountId);
        var position = new CashPosition(accountId, account.currency(), bank, book, available, Instant.now());
        positions.put(accountId, position); return position;
    }
    public CashPosition cashPosition(UUID accountId) {
        return Optional.ofNullable(positions.get(accountId)).orElseThrow(() -> new NoSuchElementException("Unknown cash position"));
    }
    public CashForecast forecast(UUID id, ForecastMethod method, LocalDate asOf,
                                 List<ForecastLine> lines) {
        var result = new CashForecast(id, method, asOf, lines); forecasts.put(id, result); return result;
    }
    public CashForecast forecastDirect(UUID id, LocalDate asOf, int periods, String currency,
                                       DirectAmountSource source) {
        return forecast(id, ForecastMethod.DIRECT, asOf, new DirectForecastEngine(source).forecast(asOf, periods, currency));
    }
    public CashForecast forecastIndirect(UUID id, LocalDate asOf, String currency,
                                         Supplier<BigDecimal> netIncome, Supplier<BigDecimal> nonCash,
                                         Supplier<BigDecimal> workingCapital) {
        return forecast(id, ForecastMethod.INDIRECT, asOf,
                new IndirectForecastEngine(netIncome, nonCash, workingCapital).forecast(asOf, 1, currency));
    }
    @FunctionalInterface public interface DirectAmountSource {
        BigDecimal amountFor(String category, LocalDate start, LocalDate end);
    }
    public static final class DirectForecastEngine implements ForecastEngine {
        private final DirectAmountSource source;
        public DirectForecastEngine(DirectAmountSource source) { this.source = Objects.requireNonNull(source); }
        public ForecastMethod method() { return ForecastMethod.DIRECT; }
        public List<ForecastLine> forecast(LocalDate asOf, int periods, String currency) {
            var result = new ArrayList<ForecastLine>();
            var categories = List.of("OPERATING_INFLOW", "OPERATING_OUTFLOW", "INVESTING_INFLOW",
                    "INVESTING_OUTFLOW", "FINANCING_INFLOW", "FINANCING_OUTFLOW");
            for (int i = 0; i < periods; i++) {
                var start = asOf.plusDays(i * 7L); var end = start.plusDays(6);
                for (var category : categories) {
                    var amount = source.amountFor(category, start, end);
                    if (amount != null && amount.signum() != 0) result.add(new ForecastLine(start, end, category, amount, currency));
                }
            }
            return result;
        }
    }
    public static final class IndirectForecastEngine implements ForecastEngine {
        private final Supplier<BigDecimal> netIncome, nonCash, workingCapital;
        public IndirectForecastEngine(Supplier<BigDecimal> netIncome, Supplier<BigDecimal> nonCash, Supplier<BigDecimal> workingCapital) {
            this.netIncome = Objects.requireNonNull(netIncome); this.nonCash = Objects.requireNonNull(nonCash); this.workingCapital = Objects.requireNonNull(workingCapital);
        }
        public ForecastMethod method() { return ForecastMethod.INDIRECT; }
        public List<ForecastLine> forecast(LocalDate asOf, int periods, String currency) {
            var operating = netIncome.get().add(nonCash.get()).subtract(workingCapital.get());
            return List.of(new ForecastLine(asOf, asOf, operating.signum() >= 0 ? "OPERATING_INFLOW" : "OPERATING_OUTFLOW",
                    operating.abs(), currency));
        }
    }
    public LiquidityPlan createLiquidityPlan(String currency, BigDecimal minimum, BigDecimal target,
                                             BigDecimal maximum, UUID sweepAccountId) {
        var plan = new LiquidityPlan(UUID.randomUUID(), currency, minimum, target, maximum, sweepAccountId);
        liquidityPlans.put(plan.id(), plan); return plan;
    }
    public NettingResult net(List<NettingPosition> gross) {
        var grouped = new LinkedHashMap<String, NettingPosition>();
        for (var p : gross) grouped.merge(p.companyId(), p, (a, b) ->
                new NettingPosition(a.companyId(), a.currency(), a.receivable().add(b.receivable()), a.payable().add(b.payable())));
        var result = List.copyOf(grouped.values());
        var residual = result.stream().map(NettingPosition::net).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new NettingResult(result, residual);
    }
    public NettingCycle createNettingCycle(String currency, List<NettingPosition> positions) {
        var cycle = new NettingCycle(UUID.randomUUID(), currency, positions, NettingStatus.DRAFT, Instant.now());
        nettingCycles.put(cycle.id(), cycle); return cycle;
    }
    public NettingCycle readyNettingCycle(UUID id) {
        var current = requireNettingCycle(id);
        var result = net(current.positions());
        if (result.residual().signum() != 0) throw new IllegalStateException("Netting cycle is imbalanced");
        var updated = new NettingCycle(id, current.currency(), result.positions(), NettingStatus.READY, Instant.now());
        nettingCycles.put(id, updated); return updated;
    }
    public NettingCycle executeNettingCycle(UUID id) {
        var current = requireNettingCycle(id);
        if (current.status() != NettingStatus.READY) throw new IllegalStateException("Netting cycle must be ready");
        var updated = new NettingCycle(id, current.currency(), current.positions(), NettingStatus.EXECUTED, Instant.now());
        nettingCycles.put(id, updated); return updated;
    }
    public FxExposure recordExposure(String from, String to, BigDecimal amount, LocalDate maturity) {
        var exposure = new FxExposure(UUID.randomUUID(), from, to, amount, maturity, BigDecimal.ZERO);
        exposures.put(exposure.id(), exposure); return exposure;
    }
    public FxExposure designateHedge(UUID exposureId, BigDecimal amount) {
        var exposure = requireExposure(exposureId);
        var updated = new FxExposure(exposure.id(), exposure.fromCurrency(), exposure.toCurrency(), exposure.amount(),
                exposure.maturity(), exposure.hedgedAmount().add(amount));
        exposures.put(exposureId, updated); return updated;
    }
    public HedgeDesignation designateHedge(UUID exposureId, HedgeType type, BigDecimal notional, BigDecimal rate) {
        var designation = new HedgeDesignation(UUID.randomUUID(), exposureId, type, notional, rate, Instant.now());
        designations.put(designation.id(), designation); return designation;
    }
    public HedgeEffectiveness testEffectiveness(UUID designationId, BigDecimal hedgeChange, BigDecimal exposureChange) {
        Objects.requireNonNull(hedgeChange); Objects.requireNonNull(exposureChange);
        if (exposureChange.signum() == 0) throw new IllegalArgumentException("exposureChange must not be zero");
        var ratio = hedgeChange.abs().divide(exposureChange.abs(), 8, RoundingMode.HALF_UP);
        return new HedgeEffectiveness(designationId, ratio, ratio.compareTo(new BigDecimal("0.8")) >= 0
                && ratio.compareTo(new BigDecimal("1.25")) <= 0, Instant.now());
    }
    public CashPool createCashPool(String currency, PoolingType type, UUID header, List<UUID> participants) {
        var pool = new CashPool(UUID.randomUUID(), currency, type, header, participants);
        pools.put(pool.id(), pool); return pool;
    }
    public DebtFacility createDebt(String currency, BigDecimal limit, BigDecimal rate, LocalDate maturity) {
        var debt = new DebtFacility(UUID.randomUUID(), currency, limit, BigDecimal.ZERO, rate, maturity, BigDecimal.ZERO, DebtStatus.ACTIVE);
        debts.put(debt.id(), debt); return debt;
    }
    public DebtFacility draw(UUID id, BigDecimal amount) {
        var d = requireDebt(id); if (amount.signum() <= 0 || amount.compareTo(d.available()) > 0) throw new IllegalArgumentException("draw exceeds availability");
        var updated = new DebtFacility(id, d.currency(), d.limit(), d.drawn().add(amount), d.annualRate(), d.maturity(), d.accruedInterest(), d.status());
        debts.put(id, updated); return updated;
    }
    public DebtFacility repay(UUID id, BigDecimal amount) {
        var d = requireDebt(id); if (amount.signum() <= 0 || amount.compareTo(d.drawn()) > 0) throw new IllegalArgumentException("repay exceeds drawn balance");
        var updated = new DebtFacility(id, d.currency(), d.limit(), d.drawn().subtract(amount), d.annualRate(), d.maturity(), d.accruedInterest(), d.status());
        debts.put(id, updated); return updated;
    }
    public DebtFacility accrueInterest(UUID id, int days) {
        var d = requireDebt(id);
        var interest = d.drawn().multiply(d.annualRate()).multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(365), 8, RoundingMode.HALF_UP);
        var updated = new DebtFacility(id, d.currency(), d.limit(), d.drawn(), d.annualRate(), d.maturity(), d.accruedInterest().add(interest), d.status());
        debts.put(id, updated); return updated;
    }
    public InvestmentPosition invest(String currency, BigDecimal principal, BigDecimal rate, LocalDate maturity) {
        var investment = new InvestmentPosition(UUID.randomUUID(), currency, principal, rate, maturity, InvestmentStatus.ACTIVE);
        investments.put(investment.id(), investment); return investment;
    }
    public BigDecimal investmentMaturityValue(UUID id) {
        var investment = requireInvestment(id);
        return investment.principal().add(investment.principal().multiply(investment.rate()));
    }
    public InvestmentPosition matureInvestment(UUID id) {
        var investment = requireInvestment(id);
        var updated = new InvestmentPosition(id, investment.currency(), investment.principal(),
                investment.rate(), investment.maturity(), InvestmentStatus.MATURED);
        investments.put(id, updated); return updated;
    }
    public InvestmentPosition redeemInvestment(UUID id) {
        var investment = requireInvestment(id);
        if (investment.status() == InvestmentStatus.REDEEMED) throw new IllegalStateException("Investment already redeemed");
        var updated = new InvestmentPosition(id, investment.currency(), investment.principal(),
                investment.rate(), investment.maturity(), InvestmentStatus.REDEEMED);
        investments.put(id, updated); return updated;
    }
    public TreasuryView view() { return new TreasuryView(List.copyOf(accounts.values()), List.copyOf(positions.values()), List.copyOf(batches.values()), Instant.now()); }
    public Collection<CashForecast> forecasts() { return List.copyOf(forecasts.values()); }
    public Map<String, Object> dataset() {
        return Map.of("accounts", List.copyOf(accounts.values()),
                "cashPositions", List.copyOf(positions.values()),
                "forecasts", List.copyOf(forecasts.values()),
                "paymentBatches", List.copyOf(batches.values()),
                "liquidityPlans", List.copyOf(liquidityPlans.values()),
                "fxExposures", List.copyOf(exposures.values()),
                "cashPools", List.copyOf(pools.values()),
                "nettingCycles", List.copyOf(nettingCycles.values()),
                "debtFacilities", List.copyOf(debts.values()),
                "investments", List.copyOf(investments.values()));
    }

    private BankAccount requireAccount(UUID id) { return Optional.ofNullable(accounts.get(id)).orElseThrow(() -> new NoSuchElementException("Unknown bank account: " + id)); }
    private FxExposure requireExposure(UUID id) { return Optional.ofNullable(exposures.get(id)).orElseThrow(() -> new NoSuchElementException("Unknown FX exposure: " + id)); }
    private DebtFacility requireDebt(UUID id) { return Optional.ofNullable(debts.get(id)).orElseThrow(() -> new NoSuchElementException("Unknown debt facility: " + id)); }
    private NettingCycle requireNettingCycle(UUID id) { return Optional.ofNullable(nettingCycles.get(id)).orElseThrow(() -> new NoSuchElementException("Unknown netting cycle: " + id)); }
    private InvestmentPosition requireInvestment(UUID id) { return Optional.ofNullable(investments.get(id)).orElseThrow(() -> new NoSuchElementException("Unknown investment: " + id)); }
    private static String upper(String value) { return text(value, "currency").toUpperCase(Locale.ROOT); }
    private static String text(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank"); return value; }
}
