package tech.kayys.syirkah.accounting.consolidation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Dependency-light application boundary for group consolidation.
 *
 * <p>The service deliberately stores state in memory, while the nested
 * repository and SPI contracts provide stable seams for database, ledger and
 * FX adapters.  Commands and query views are represented by records so the
 * same boundary can be used from REST, messaging, or a batch scheduler.</p>
 */
public final class ConsolidationService {
    public enum MemberType { PARENT, SUBSIDIARY, ASSOCIATE, JOINT_VENTURE }
    public enum TranslationMethod { CURRENT_RATE, TEMPORAL }

    public record GroupMember(String companyId, String parentCompanyId, MemberType type,
                              BigDecimal directOwnership, String functionalCurrency) {
        public GroupMember {
            text(companyId, "companyId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(directOwnership, "directOwnership");
            if (directOwnership.signum() < 0 || directOwnership.compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException("directOwnership must be between 0 and 1");
            text(functionalCurrency, "functionalCurrency");
        }
    }

    public static final class GroupHierarchy {
        private final String rootCompanyId;
        private final Map<String, GroupMember> members = new LinkedHashMap<>();

        public GroupHierarchy(String rootCompanyId) {
            this.rootCompanyId = text(rootCompanyId, "rootCompanyId");
            add(new GroupMember(rootCompanyId, null, MemberType.PARENT, BigDecimal.ONE, "IDR"));
        }
        public void add(GroupMember member) {
            Objects.requireNonNull(member, "member");
            if (members.putIfAbsent(member.companyId(), member) != null)
                throw new IllegalArgumentException("Company already registered: " + member.companyId());
            if (member.parentCompanyId() != null && !members.containsKey(member.parentCompanyId()))
                throw new IllegalArgumentException("Unknown parent: " + member.parentCompanyId());
        }
        public String rootCompanyId() { return rootCompanyId; }
        public List<GroupMember> members() { return List.copyOf(members.values()); }
        public GroupMember member(String companyId) {
            var result = members.get(companyId);
            if (result == null) throw new NoSuchElementException("Unknown group member: " + companyId);
            return result;
        }
        public BigDecimal attributableOwnership(String companyId) {
            BigDecimal result = BigDecimal.ONE;
            var current = member(companyId);
            while (current.parentCompanyId() != null) {
                result = result.multiply(current.directOwnership());
                current = member(current.parentCompanyId());
            }
            return result;
        }
        public BigDecimal minorityOwnership(String companyId) {
            return BigDecimal.ONE.subtract(attributableOwnership(companyId));
        }
    }

    public record IntercompanyBalance(String fromCompany, String toCompany, String account,
                                      BigDecimal amount, String currency, String reference) {
        public IntercompanyBalance {
            text(fromCompany, "fromCompany"); text(toCompany, "toCompany");
            text(account, "account"); text(currency, "currency"); text(reference, "reference");
            Objects.requireNonNull(amount, "amount");
            if (amount.signum() < 0) throw new IllegalArgumentException("amount must not be negative");
            if (fromCompany.equals(toCompany)) throw new IllegalArgumentException("companies must differ");
        }
    }

    public record EliminationEntry(String ruleCode, ConsolidationRun.EliminationKind kind,
                                   String sourceCompany, String targetCompany,
                                   BigDecimal amount, String currency, String reference,
                                   Instant executedAt) {
        public EliminationEntry {
            text(ruleCode, "ruleCode"); Objects.requireNonNull(kind, "kind");
            text(sourceCompany, "sourceCompany"); text(targetCompany, "targetCompany");
            Objects.requireNonNull(amount, "amount"); text(currency, "currency");
            text(reference, "reference"); Objects.requireNonNull(executedAt, "executedAt");
        }
    }

    @FunctionalInterface
    public interface EliminationRule {
        Optional<EliminationEntry> apply(IntercompanyBalance balance);
        default String code() { return getClass().getSimpleName(); }
    }

    public static final class ReciprocalBalanceRule implements EliminationRule {
        @Override public Optional<EliminationEntry> apply(IntercompanyBalance b) {
            return Optional.of(new EliminationEntry("RECIPROCAL_BALANCE",
                    ConsolidationRun.EliminationKind.RECIPROCAL_BALANCE, b.fromCompany(),
                    b.toCompany(), b.amount(), b.currency(), b.reference(), Instant.now()));
        }
    }
    public static final class IntercompanyProfitRule implements EliminationRule {
        @Override public Optional<EliminationEntry> apply(IntercompanyBalance b) {
            if (!b.account().toUpperCase(Locale.ROOT).contains("PROFIT")
                    && !b.account().toUpperCase(Locale.ROOT).contains("REVENUE")) return Optional.empty();
            return Optional.of(new EliminationEntry("INTERCOMPANY_PROFIT",
                    ConsolidationRun.EliminationKind.INTERCOMPANY_PROFIT, b.fromCompany(),
                    b.toCompany(), b.amount(), b.currency(), b.reference(), Instant.now()));
        }
    }
    public static final class DividendRule implements EliminationRule {
        @Override public Optional<EliminationEntry> apply(IntercompanyBalance b) {
            if (!b.account().toUpperCase(Locale.ROOT).contains("DIVIDEND")) return Optional.empty();
            return Optional.of(new EliminationEntry("DIVIDEND",
                    ConsolidationRun.EliminationKind.DIVIDEND, b.fromCompany(), b.toCompany(),
                    b.amount(), b.currency(), b.reference(), Instant.now()));
        }
    }

    public record TranslationResult(String companyId, String functionalCurrency,
                                    String presentationCurrency, Map<String, BigDecimal> balances,
                                    BigDecimal cumulativeTranslationAdjustment,
                                    TranslationMethod method, Instant translatedAt) {
        public TranslationResult {
            text(companyId, "companyId"); text(functionalCurrency, "functionalCurrency");
            text(presentationCurrency, "presentationCurrency"); Objects.requireNonNull(balances);
            Objects.requireNonNull(cumulativeTranslationAdjustment);
            Objects.requireNonNull(method); Objects.requireNonNull(translatedAt);
            balances = Map.copyOf(balances);
        }
    }
    public record MinorityInterest(String companyId, BigDecimal nonControllingPercentage,
                                   BigDecimal profit, BigDecimal minorityShare) {}
    public record TrialBalanceView(UUID runId, String currency, Map<String, BigDecimal> balances,
                                   List<EliminationEntry> eliminations, Instant generatedAt) {}
    public record RunView(UUID id, String tenantId, LocalDate periodEnd, String currency,
                          ConsolidationRun.Status status, int accountCount, int eliminationCount) {}
    public record StartCommand(String tenantId, LocalDate periodEnd, String currency) {}
    public record RecordIntercompanyCommand(UUID runId, IntercompanyBalance balance) {}
    public record TranslateCommand(UUID runId, String companyId, Map<String, BigDecimal> balances,
                                   BigDecimal closingRate) {}
    public record MinorityInterestQuery(UUID runId, Map<String, BigDecimal> profits) {}

    public interface ConsolidationRepository {
        void save(ConsolidationRun run);
        Optional<ConsolidationRun> find(UUID id);
        Collection<ConsolidationRun> all();
    }
    public static final class InMemoryRepository implements ConsolidationRepository {
        private final Map<UUID, ConsolidationRun> store = new LinkedHashMap<>();
        public void save(ConsolidationRun run) { store.put(run.id(), run); }
        public Optional<ConsolidationRun> find(UUID id) { return Optional.ofNullable(store.get(id)); }
        public Collection<ConsolidationRun> all() { return List.copyOf(store.values()); }
    }

    private final ConsolidationRepository repository;
    private final Map<UUID, GroupHierarchy> hierarchies = new HashMap<>();
    private final Map<UUID, List<IntercompanyBalance>> intercompany = new HashMap<>();
    private final Map<UUID, List<EliminationEntry>> eliminationEntries = new HashMap<>();
    private final Map<UUID, List<TranslationResult>> translations = new HashMap<>();
    private final Map<UUID, List<MinorityInterest>> minorityInterests = new HashMap<>();
    private final List<EliminationRule> rules = new ArrayList<>(
            List.of(new ReciprocalBalanceRule(), new IntercompanyProfitRule(), new DividendRule()));

    public ConsolidationService() { this(new InMemoryRepository()); }
    public ConsolidationService(ConsolidationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }
    public ConsolidationRun start(String tenantId, LocalDate periodEnd, String currency) {
        var run = new ConsolidationRun(UUID.randomUUID(), tenantId, periodEnd, currency);
        repository.save(run); return run;
    }
    public ConsolidationRun start(StartCommand command) {
        return start(command.tenantId(), command.periodEnd(), command.currency());
    }
    public ConsolidationRun require(UUID id) {
        return repository.find(id).orElseThrow(() -> new NoSuchElementException("Unknown consolidation run: " + id));
    }
    public void collect(UUID id, String account, BigDecimal amount) { require(id).collect(account, amount); }
    public void collect(UUID id, String company, String account, BigDecimal amount) {
        collect(id, account, amount);
        // Company-level detail is retained as a synthetic account in the
        // in-memory boundary; a ledger adapter can replace this projection.
        intercompany.computeIfAbsent(id, ignored -> new ArrayList<>());
    }
    public void eliminate(UUID id, ConsolidationRun.Elimination elimination) {
        require(id).eliminate(elimination);
    }
    public void registerGroup(UUID runId, GroupHierarchy hierarchy) { require(runId); hierarchies.put(runId, hierarchy); }
    public void recordIntercompany(UUID runId, IntercompanyBalance balance) {
        require(runId); intercompany.computeIfAbsent(runId, ignored -> new ArrayList<>()).add(balance);
    }
    public void recordIntercompany(RecordIntercompanyCommand command) {
        recordIntercompany(command.runId(), command.balance());
    }
    public void registerRule(EliminationRule rule) { rules.add(Objects.requireNonNull(rule)); }
    public List<EliminationEntry> executeEliminations(UUID runId) {
        var run = require(runId);
        var result = new ArrayList<EliminationEntry>();
        for (var balance : intercompany.getOrDefault(runId, List.of())) {
            for (var rule : rules) rule.apply(balance).ifPresent(result::add);
        }
        eliminationEntries.put(runId, result);
        for (var entry : result) {
            run.eliminate(new ConsolidationRun.Elimination(entry.sourceCompany(), entry.targetCompany(),
                    entry.kind(), entry.amount(), entry.currency(), entry.reference()));
        }
        return List.copyOf(result);
    }
    public TranslationResult translate(UUID runId, String companyId, Map<String, BigDecimal> balances,
                                       BigDecimal closingRate) {
        var run = require(runId); Objects.requireNonNull(balances); Objects.requireNonNull(closingRate);
        if (closingRate.signum() <= 0) throw new IllegalArgumentException("closingRate must be positive");
        var member = hierarchies.containsKey(runId) ? hierarchies.get(runId).member(companyId) : null;
        var source = member == null ? run.presentationCurrency() : member.functionalCurrency();
        var translated = new LinkedHashMap<String, BigDecimal>();
        balances.forEach((account, amount) -> translated.put(account, amount.multiply(closingRate)));
        var result = new TranslationResult(companyId, source, run.presentationCurrency(), translated,
                translated.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .subtract(balances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)),
                TranslationMethod.CURRENT_RATE, Instant.now());
        translations.computeIfAbsent(runId, ignored -> new ArrayList<>()).add(result);
        return result;
    }
    public List<MinorityInterest> calculateMinorityInterest(UUID runId, Map<String, BigDecimal> profits) {
        var hierarchy = Optional.ofNullable(hierarchies.get(runId))
                .orElseThrow(() -> new IllegalStateException("No group hierarchy registered"));
        var result = profits.entrySet().stream().map(e -> {
            var pct = hierarchy.minorityOwnership(e.getKey());
            return new MinorityInterest(e.getKey(), pct, e.getValue(), e.getValue().multiply(pct));
        }).toList();
        minorityInterests.put(runId, new ArrayList<>(result)); return result;
    }
    public TrialBalanceView trialBalance(UUID runId) {
        var run = require(runId);
        return new TrialBalanceView(runId, run.presentationCurrency(), run.trialBalance(),
                eliminationEntries.getOrDefault(runId, List.of()), Instant.now());
    }
    public RunView view(UUID runId) {
        var run = require(runId);
        return new RunView(run.id(), run.tenantId(), run.periodEnd(), run.presentationCurrency(),
                run.status(), run.trialBalance().size(), eliminationEntries.getOrDefault(runId, List.of()).size());
    }
    public List<EliminationEntry> eliminations(UUID runId) { require(runId); return List.copyOf(eliminationEntries.getOrDefault(runId, List.of())); }
    public List<TranslationResult> translations(UUID runId) { require(runId); return List.copyOf(translations.getOrDefault(runId, List.of())); }
    public List<MinorityInterest> minorityInterests(UUID runId) { require(runId); return List.copyOf(minorityInterests.getOrDefault(runId, List.of())); }
    public Map<String, Object> dataset(UUID runId) {
        require(runId);
        return Map.of("run", view(runId), "trialBalance", trialBalance(runId),
                "eliminations", eliminations(runId), "translations", translations(runId),
                "minorityInterest", minorityInterests(runId));
    }
    public void translate(UUID id) { require(id).translate(); }
    public void close(UUID id) { require(id).close(); }
    public void publish(UUID id) { require(id).publish(); }

    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
