package tech.kayys.numou.numourest;

import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.numouapplication.governance.PendingApproval;
import tech.kayys.numou.numouapplication.query.SyirkahSummary;
import tech.kayys.numou.numoudomain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Wire format. Ids are UUID strings, amounts are plain decimals in the syirkah's own currency, enums travel by name.
 * Requests never carry a tenant or an actor: those come from the verified request context.
 */
public final class SyirkahDtos {

    private SyirkahDtos() {
    }

    // ------------------------------------------------------------ requests

    public record PartnerDto(String id, String name, String role, boolean contributesWork) {
    }

    public record CapitalDto(String partner, String kind, BigDecimal amount, String valuationNote) {
    }

    public record ContractDto(int version, String type, String currency, List<PartnerDto> partners,
                              List<CapitalDto> capital, Map<String, BigDecimal> nisbahPercent, String profitBasis,
                              Map<String, BigDecimal> lossSharing, List<String> declaredClauses,
                              List<String> witnesses, boolean ijabQabulRecorded, LocalDate effectiveFrom) {
    }

    public record PartyDto(String partnerId, LocalDate dateOfBirth, String residence, boolean soundMind,
                           boolean authorityOverCapital, boolean freeConsent, String ijabQabulStatement) {
    }

    public record LawfulnessReviewDto(String reviewedBy, LocalDate reviewedOn, boolean lawful) {
    }

    public record WitnessDto(String name, String sex) {
    }

    /** How the akad was formed and written; see {@code AkadFormation}. Lists may be omitted when empty. */
    public record FormationDto(List<PartyDto> parties, String businessActivity, LawfulnessReviewDto lawfulnessReview,
                               List<String> combinedAkad, List<String> unresolvedAmbiguities, boolean sameSession,
                               boolean ijabWithdrawn, List<String> sectionsPresent, String signedPlace,
                               LocalDate signedOn, List<WitnessDto> witnesses) {
    }

    /** Omitted fields mean the book's own positions: nishab witnesses, multi-akad blocked, no minimum age. */
    public record FormationPolicyDto(String witnessRule, Boolean blockMultiAkad, Integer minimumAgeYears) {
    }

    /**
     * {@code syirkahId} is optional; supply it to make creation idempotent across client retries.
     * {@code formation} is optional unless the deployment requires the akad formation checks; when present the
     * syirkah is opened only if every check passes, and the record is stored with it.
     */
    public record OpenRequest(String syirkahId, int amountScale, ContractDto contract, FormationDto formation,
                              FormationPolicyDto formationPolicy) {
    }

    public record FinancialResultDto(BigDecimal revenue, BigDecimal directCosts, BigDecimal operatingCosts,
                                     BigDecimal otherCosts) {
    }

    public record ManagerFaultDto(boolean negligentOrBreach, BigDecimal attributableLoss, String reason) {
    }

    /** {@code closeId} is optional; supply it to make a retry idempotent. */
    public record ClosePeriodRequest(String closeId, LocalDate periodStart, LocalDate periodEnd,
                                     FinancialResultDto result, ManagerFaultDto fault, String disposition,
                                     TradingDto trading) {
    }

    public record PaymentRequest(String partnerId, BigDecimal amount, String reference) {
    }

    public record ConsentDto(String partnerId, boolean approved, LocalDate decidedOn, String evidence) {
    }

    public record SyufahDto(String partnerId, boolean exercised, LocalDate answeredOn) {
    }

    public record SuccessionDto(String deceasedId, String heirId, boolean continues, LocalDate decidedOn,
                                String evidence) {
    }

    /** kind is NEW_CAPITAL, PARTNER_REPLACEMENT or PARTNER_DEATH; profitDecision is DISTRIBUTE or RETAIN. */
    public record LifecycleDto(String kind, List<String> affected, LocalDate eventDate, List<ConsentDto> consents,
                               String profitDecision, List<SyufahDto> syufah, List<SuccessionDto> successions) {
    }

    public record LifecycleRecordView(int contractVersion, LifecycleDto lifecycle, Instant recordedAt) {
    }

    /** {@code lifecycle} is optional; when present, approvals come from its consents and {@code approvals} is ignored. */
    public record ReviseRequest(ContractDto next, List<String> approvals, LifecycleDto lifecycle) {
    }

    /** One replacement id per effective close from the target onwards; omitted ids are generated. */
    public record CorrectCloseRequest(String targetCloseId, List<String> replacementCloseIds,
                                      FinancialResultDto corrected, ManagerFaultDto fault, String disposition) {
    }

    public record AssetSaleDto(String description, BigDecimal proceeds, String buyer, String buyerPartner,
                               boolean shufahOffered) {
    }

    /** Either {@code sales} (preferred: syuf'ah is checked per asset) or the aggregate figures. */
    public record LiquidateRequest(List<AssetSaleDto> sales, BigDecimal assetProceeds, boolean shufahOffered,
                                   BigDecimal externalDebts, LocalDate liquidationDate, ManagerFaultDto fault) {
    }

    public record RejectRequest(String reason) {
    }

    // ------------------------------------------------------------ responses

    public record PositionView(String partnerId, String name, BigDecimal capitalBasis, BigDecimal capitalBalance,
                               BigDecimal profitPayable, BigDecimal liability) {
    }

    public record SummaryView(String id, String type, String currency, String status, int contractVersion,
                              int partnerCount, int closeCount, LocalDate latestCloseEnd, int version,
                              Instant createdAt, Instant updatedAt) {
    }

    public record SyirkahView(SummaryView summary, ContractDto contract, List<PositionView> positions) {
    }

    public record Page(List<SummaryView> items, String nextCursor) {
    }

    public record RuleView(int id, String title, String source, String enforcement, String adviser,
                           java.util.List<String> codes, java.util.List<String> testedBy, String note) {
    }

    public record RulesView(int count, int waitingForAdviser, boolean frozen, String adviserSignoff,
                            String fingerprint, java.util.List<RuleView> rules) {
    }

    public record StaffRequest(String employer, String employeePartner, String workType, String workPeriod,
                               String effort, String wageBasis, BigDecimal wageAmount, String payPeriod,
                               List<String> jaliClauses, boolean jaliReviewedLawful) {
    }

    public record StaffView(int sequence, int contractVersion, String employer, String employeePartner,
                            String workType, String workPeriod, String effort, String wageBasis,
                            BigDecimal wageAmount, String payPeriod, List<String> jaliClauses,
                            boolean jaliReviewedLawful, Instant recordedAt) {
    }

    public record StatementLineDto(String group, String label, BigDecimal amount) {
    }

    public record BalanceSheetRequest(String name, LocalDate asOf, List<StatementLineDto> assets,
                                      List<StatementLineDto> liabilities) {
    }

    public record StatementLineView(String group, String label, BigDecimal amount) {
    }

    public record CapitalLineView(String partner, String name, BigDecimal capital, BigDecimal profitPayable) {
    }

    public record BalanceSheetView(String name, LocalDate asOf, List<StatementLineView> assets,
                                   BigDecimal partnerReceivable, BigDecimal totalAssets,
                                   List<StatementLineView> liabilities, BigDecimal totalLiabilities,
                                   List<CapitalLineView> capital, BigDecimal unallocatedDeficit,
                                   BigDecimal totalCapital, BigDecimal totalLiabilitiesAndCapital,
                                   BigDecimal difference, boolean balanced) {
    }

    public record IncomeStatementView(String name, LocalDate periodStart, LocalDate periodEnd, BigDecimal revenue,
                                      BigDecimal costOfGoodsSold, BigDecimal grossProfit,
                                      BigDecimal operatingCosts, BigDecimal otherCosts, BigDecimal netResult,
                                      String result) {
    }

    public record CapitalChangeRow(String partner, String name, BigDecimal opening, BigDecimal contributions,
                                   BigDecimal returns, BigDecimal retainedProfit, BigDecimal capitalRestored,
                                   BigDecimal lossesAllocated, BigDecimal deficitReclassified, BigDecimal closing) {
    }

    public record CapitalChangesView(LocalDate from, LocalDate to, List<CapitalChangeRow> rows) {
    }

    public record CloseView(String id, int sequence, LocalDate periodStart, LocalDate periodEnd, int contractVersion,
                            BigDecimal netResult, String disposition, String status, String supersedes,
                            Instant closedAt, String hash) {
    }

    public record ReconciliationView(boolean balanced, BigDecimal partnerEquity, BigDecimal netCashContributed,
                                     BigDecimal recognizedResult, BigDecimal difference) {
    }

    public record IntegrityView(boolean ok, List<String> problems) {
    }

    public record ApprovalView(String id, String syirkahId, String kind, String maker, Instant proposedAt,
                               String status, String checker, Instant decidedAt, String note) {
    }

    public record AuditView(int checked, boolean ok, List<AuditFindingView> findings) {
    }

    public record AuditFindingView(String syirkahId, List<String> problems) {
    }

    public record LiquidationView(BigDecimal residual, BigDecimal netResult, List<PayoutView> payouts, String hash) {
    }

    public record PayoutView(String partnerId, BigDecimal cashReceived, BigDecimal owed) {
    }

    // ------------------------------------------------------------ request -> domain

    static UUID uuid(String s, String field) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " is not a valid id: " + s);
        }
    }

    static LifecycleChange lifecycle(LifecycleDto d) {
        Set<PartnerId> affected = new java.util.TreeSet<>();
        for (String x : d.affected() == null ? List.<String>of() : d.affected()) {
            affected.add(partner(x, "lifecycle.affected"));
        }
        List<LifecycleConsent> consents = new ArrayList<>();
        for (ConsentDto c : d.consents() == null ? List.<ConsentDto>of() : d.consents()) {
            consents.add(new LifecycleConsent(partner(c.partnerId(), "lifecycle.consents.partnerId"),
                    c.approved(), required(c.decidedOn(), "lifecycle.consents.decidedOn"), c.evidence()));
        }
        List<SyufahResponse> syufah = new ArrayList<>();
        for (SyufahDto s : d.syufah() == null ? List.<SyufahDto>of() : d.syufah()) {
            syufah.add(new SyufahResponse(partner(s.partnerId(), "lifecycle.syufah.partnerId"), s.exercised(),
                    required(s.answeredOn(), "lifecycle.syufah.answeredOn")));
        }
        List<Succession> successions = new ArrayList<>();
        for (SuccessionDto s : d.successions() == null ? List.<SuccessionDto>of() : d.successions()) {
            successions.add(new Succession(partner(s.deceasedId(), "lifecycle.successions.deceasedId"),
                    partner(s.heirId(), "lifecycle.successions.heirId"), s.continues(),
                    required(s.decidedOn(), "lifecycle.successions.decidedOn"), s.evidence()));
        }
        return new LifecycleChange(enumOf(LifecycleKind.class, d.kind(), "lifecycle.kind"), affected,
                required(d.eventDate(), "lifecycle.eventDate"), consents,
                d.profitDecision() == null ? null
                        : enumOf(ProfitDisposition.class, d.profitDecision(), "lifecycle.profitDecision"),
                syufah, successions);
    }

    static PartnerId partner(String s, String field) {
        return new PartnerId(uuid(s, field));
    }

    static <E extends Enum<E>> E enumOf(Class<E> type, String s, String field) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return Enum.valueOf(type, s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be one of " + Arrays.toString(type.getEnumConstants()));
        }
    }

    static <T> T required(T v, String field) {
        if (v == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return v;
    }

    static Money money(BigDecimal amount, Currency currency, String field) {
        return new Money(required(amount, field), currency);
    }

    static AkadFormation formation(FormationDto d) {
        List<PartyRecord> parties = new ArrayList<>();
        for (PartyDto p : d.parties() == null ? List.<PartyDto>of() : d.parties()) {
            parties.add(new PartyRecord(partner(p.partnerId(), "formation.parties.partnerId"), p.dateOfBirth(),
                    p.residence(), p.soundMind(), p.authorityOverCapital(), p.freeConsent(),
                    p.ijabQabulStatement()));
        }
        AkadFormation.LawfulnessReview review = d.lawfulnessReview() == null ? null
                : new AkadFormation.LawfulnessReview(d.lawfulnessReview().reviewedBy(),
                        d.lawfulnessReview().reviewedOn(), d.lawfulnessReview().lawful());
        Set<AkadSection> sections = EnumSet.noneOf(AkadSection.class);
        for (String x : d.sectionsPresent() == null ? List.<String>of() : d.sectionsPresent()) {
            sections.add(enumOf(AkadSection.class, x, "formation.sectionsPresent"));
        }
        List<Witness> witnesses = new ArrayList<>();
        for (WitnessDto w : d.witnesses() == null ? List.<WitnessDto>of() : d.witnesses()) {
            witnesses.add(new Witness(w.name(),
                    w.sex() == null || w.sex().isBlank() ? null : enumOf(WitnessSex.class, w.sex(), "witness.sex")));
        }
        return new AkadFormation(parties, d.businessActivity(), review,
                d.combinedAkad() == null ? Set.of() : new HashSet<>(d.combinedAkad()),
                d.unresolvedAmbiguities(), d.sameSession(), d.ijabWithdrawn(), sections, d.signedPlace(),
                d.signedOn(), witnesses);
    }

    static FormationPolicy policy(FormationPolicyDto d) {
        FormationPolicy book = FormationPolicy.bookDefault();
        if (d == null) {
            return book;
        }
        return new FormationPolicy(
                d.witnessRule() == null ? book.witnessRule()
                        : enumOf(WitnessRule.class, d.witnessRule(), "formationPolicy.witnessRule"),
                d.blockMultiAkad() == null ? book.blockMultiAkad() : d.blockMultiAkad(), d.minimumAgeYears());
    }

    static SyirkahContract contract(ContractDto d) {
        required(d, "contract");
        Currency currency = Currency.of(required(d.currency(), "contract.currency"));
        List<Partner> partners = new ArrayList<>();
        for (PartnerDto p : required(d.partners(), "contract.partners")) {
            partners.add(new Partner(partner(p.id(), "partner.id"), p.name(), enumOf(PartnerRole.class, p.role(),
                    "partner.role"), p.contributesWork()));
        }
        List<CapitalContribution> capital = new ArrayList<>();
        for (CapitalDto c : d.capital() == null ? List.<CapitalDto>of() : d.capital()) {
            capital.add(new CapitalContribution(partner(c.partner(), "capital.partner"),
                    enumOf(CapitalKind.class, c.kind(), "capital.kind"), money(c.amount(), currency, "capital.amount"),
                    c.valuationNote()));
        }
        Map<PartnerId, BigDecimal> nisbah = new TreeMap<>();
        required(d.nisbahPercent(), "contract.nisbahPercent")
                .forEach((k, v) -> nisbah.put(partner(k, "nisbah partner"), required(v, "nisbah share")));
        Map<PartnerId, BigDecimal> loss = new TreeMap<>();
        if (d.lossSharing() != null) {
            d.lossSharing().forEach((k, v) -> loss.put(partner(k, "lossSharing partner"), required(v, "loss share")));
        }
        Set<ForbiddenClause> clauses = EnumSet.noneOf(ForbiddenClause.class);
        for (String c : d.declaredClauses() == null ? List.<String>of() : d.declaredClauses()) {
            clauses.add(enumOf(ForbiddenClause.class, c, "declaredClauses"));
        }
        return new SyirkahContract(d.version(), enumOf(SyirkahType.class, d.type(), "contract.type"), currency,
                partners, capital, ProfitSharingRatio.ofPercent(nisbah),
                enumOf(ProfitBasis.class, d.profitBasis() == null ? "NET_PROFIT" : d.profitBasis(), "profitBasis"),
                loss.isEmpty() ? null : loss, clauses, d.witnesses(), d.ijabQabulRecorded(),
                required(d.effectiveFrom(), "contract.effectiveFrom"));
    }

    /** Trading syirkah inputs for a period (Bab XXII); the service works out the cost of goods sold. Omitted amounts are zero. */
    public record TradingDto(BigDecimal sales, BigDecimal salesReturns, BigDecimal salesDiscounts,
                             BigDecimal openingStock, BigDecimal purchases, BigDecimal purchaseReturns,
                             BigDecimal purchaseDiscounts, BigDecimal closingStock, BigDecimal operatingCosts,
                             BigDecimal otherCosts) {
    }

    static FinancialResult trading(TradingDto d, Currency c) {
        return TradingResultCalculator.calculate(tradingInput(d, c)).financialResult();
    }

    static TradingResultCalculator.Input tradingInput(TradingDto d, Currency c) {
        required(d, "trading");
        java.util.function.BiFunction<BigDecimal, String, Money> m =
                (v, f) -> money(v == null ? BigDecimal.ZERO : v, c, "trading." + f);
        return new TradingResultCalculator.Input(m.apply(d.sales(), "sales"),
                m.apply(d.salesReturns(), "salesReturns"), m.apply(d.salesDiscounts(), "salesDiscounts"),
                m.apply(d.openingStock(), "openingStock"), m.apply(d.purchases(), "purchases"),
                m.apply(d.purchaseReturns(), "purchaseReturns"), m.apply(d.purchaseDiscounts(), "purchaseDiscounts"),
                m.apply(d.closingStock(), "closingStock"), m.apply(d.operatingCosts(), "operatingCosts"),
                m.apply(d.otherCosts(), "otherCosts"));
    }

    static TradingDto tradingView(TradingResultCalculator.Input i) {
        return new TradingDto(i.sales().amount(), i.salesReturns().amount(), i.salesDiscounts().amount(),
                i.openingStock().amount(), i.purchases().amount(), i.purchaseReturns().amount(),
                i.purchaseDiscounts().amount(), i.closingStock().amount(), i.operatingCosts().amount(),
                i.otherCosts().amount());
    }

    static FinancialResult result(FinancialResultDto d, Currency currency) {
        required(d, "result");
        return FinancialResult.of(money(d.revenue(), currency, "result.revenue"),
                money(d.directCosts() == null ? BigDecimal.ZERO : d.directCosts(), currency, "result.directCosts"),
                money(d.operatingCosts() == null ? BigDecimal.ZERO : d.operatingCosts(), currency,
                        "result.operatingCosts"),
                money(d.otherCosts() == null ? BigDecimal.ZERO : d.otherCosts(), currency, "result.otherCosts"));
    }

    static ManagerFault fault(ManagerFaultDto d, Currency currency) {
        if (d == null || !d.negligentOrBreach()) {
            return ManagerFault.none();
        }
        return ManagerFault.of(money(d.attributableLoss(), currency, "fault.attributableLoss"), d.reason());
    }

    static LiquidationInput liquidation(LiquidateRequest r, Currency currency) {
        required(r, "request");
        Money debts = money(r.externalDebts() == null ? BigDecimal.ZERO : r.externalDebts(), currency,
                "externalDebts");
        LocalDate date = required(r.liquidationDate(), "liquidationDate");
        if (r.sales() != null && !r.sales().isEmpty()) {
            List<AssetSale> sales = new ArrayList<>();
            for (AssetSaleDto s : r.sales()) {
                AssetSale.Buyer buyer = enumOf(AssetSale.Buyer.class, s.buyer(), "sale.buyer");
                sales.add(new AssetSale(s.description(), money(s.proceeds(), currency, "sale.proceeds"), buyer,
                        s.buyerPartner() == null ? null : partner(s.buyerPartner(), "sale.buyerPartner"),
                        s.shufahOffered()));
            }
            return LiquidationInput.fromSales(sales, debts, date);
        }
        return new LiquidationInput(money(r.assetProceeds(), currency, "assetProceeds"), debts, r.shufahOffered(),
                date);
    }

    // ------------------------------------------------------------ domain -> response

    static BalanceSheetView balanceSheet(BookStatements.BalanceSheet b) {
        return new BalanceSheetView(b.syirkahName(), b.asOf(),
                b.assets().stream().map(a -> new StatementLineView(a.group().name(), a.label(), a.amount().amount()))
                        .toList(),
                b.partnerReceivable().amount(), b.totalAssets().amount(),
                b.liabilities().stream()
                        .map(l -> new StatementLineView(l.group().name(), l.label(), l.amount().amount())).toList(),
                b.totalLiabilities().amount(),
                b.capital().stream().map(c -> new CapitalLineView(c.partner().value().toString(), c.name(),
                        c.capital().amount(), c.profitPayable().amount())).toList(),
                b.unallocatedDeficit().amount(), b.totalCapital().amount(),
                b.totalLiabilitiesAndCapital().amount(), b.difference().amount(), b.balanced());
    }

    static IncomeStatementView incomeStatement(BookStatements.IncomeStatement i) {
        return new IncomeStatementView(i.syirkahName(), i.periodStart(), i.periodEnd(), i.revenue().amount(),
                i.costOfGoodsSold().amount(), i.grossProfit().amount(), i.operatingCosts().amount(),
                i.otherCosts().amount(), i.netResult().amount(), i.result());
    }

    static StaffContract staff(StaffRequest r, Currency currency) {
        StaffContract.Employer employer = enumOf(StaffContract.Employer.class, r.employer(), "employer");
        PartnerId partner = r.employeePartner() == null || r.employeePartner().isBlank() ? null
                : partner(r.employeePartner(), "employeePartner");
        StaffContract.WageBasis basis = enumOf(StaffContract.WageBasis.class, r.wageBasis(), "wageBasis");
        Money amount = r.wageAmount() == null ? null : money(r.wageAmount(), currency, "wageAmount");
        return new StaffContract(employer, partner, r.workType(), r.workPeriod(), r.effort(),
                new StaffContract.Wage(basis, amount, r.payPeriod()), r.jaliClauses(), r.jaliReviewedLawful());
    }

    static StaffView staffView(tech.kayys.numou.numouapplication.port.StaffRecordPort.Recorded r) {
        StaffContract c = r.staff();
        return new StaffView(r.sequence(), r.contractVersion(), c.employer().name(),
                c.employeePartner() == null ? null : c.employeePartner().value().toString(), c.workType(),
                c.workPeriod(), c.effort(), c.wage().basis().name(),
                c.wage().fixedAmount() == null ? null : c.wage().fixedAmount().amount(), c.wage().payPeriod(),
                c.jaliClauses(), c.jaliReviewedLawful(), r.recordedAt());
    }

    static LifecycleRecordView lifecycleRecord(tech.kayys.numou.numouapplication.port.LifecycleRecordPort.Recorded r) {
        LifecycleChange c = r.change();
        return new LifecycleRecordView(r.contractVersion(), new LifecycleDto(c.kind().name(),
                c.affected().stream().map(x -> x.value().toString()).sorted().toList(), c.eventDate(),
                c.consents().stream().map(x -> new ConsentDto(x.partner().value().toString(), x.approved(),
                        x.decidedOn(), x.evidence())).toList(),
                c.profitDecision() == null ? null : c.profitDecision().name(),
                c.syufah().stream().map(x -> new SyufahDto(x.partner().value().toString(), x.exercised(),
                        x.answeredOn())).toList(),
                c.successions().stream().map(x -> new SuccessionDto(x.deceased().value().toString(),
                        x.heir().value().toString(), x.continues(), x.decidedOn(), x.evidence())).toList()),
                r.recordedAt());
    }

    static CapitalChangesView capitalChanges(LocalDate from, LocalDate to, CapitalRollForward r) {
        return new CapitalChangesView(from, to, r.rows().stream().map(x -> new CapitalChangeRow(
                x.partner().value().toString(), x.name(), x.opening().amount(), x.contributions().amount(),
                x.returns().amount(), x.retainedProfit().amount(), x.capitalRestored().amount(),
                x.lossesAllocated().amount(), x.deficitReclass().amount(), x.closing().amount())).toList());
    }

    static BigDecimal amt(Money m) {
        return m.amount();
    }

    static SummaryView summary(SyirkahSummary s) {
        return new SummaryView(s.id().value().toString(), s.type().name(), s.currency(), s.status().name(),
                s.contractVersion(), s.partnerCount(), s.closeCount(), s.latestCloseEnd(), s.version(),
                s.createdAt(), s.updatedAt());
    }

    static ContractDto contractView(SyirkahContract c) {
        List<PartnerDto> partners = c.partners().stream().map(p -> new PartnerDto(p.id().value().toString(),
                p.name(), p.role().name(), p.contributesWork())).toList();
        List<CapitalDto> capital = c.capital().stream().map(k -> new CapitalDto(k.partner().value().toString(),
                k.kind().name(), k.amount().amount(), k.valuationNote())).toList();
        Map<String, BigDecimal> nisbah = new LinkedHashMap<>();
        c.nisbah().shares().forEach((p, v) -> nisbah.put(p.value().toString(), v.multiply(BigDecimal.valueOf(100))
                .stripTrailingZeros()));
        Map<String, BigDecimal> loss = new LinkedHashMap<>();
        c.lossSharing().forEach((p, v) -> loss.put(p.value().toString(), v));
        return new ContractDto(c.version(), c.type().name(), c.currency().code(), partners, capital, nisbah,
                c.profitBasis().name(), loss, c.declaredClauses().stream().map(Enum::name).sorted().toList(),
                c.witnesses(), c.ijabQabulRecorded(), c.effectiveFrom());
    }

    static SyirkahView syirkah(Syirkah s) {
        List<PositionView> positions = new ArrayList<>();
        for (Partner p : s.contract().partners()) {
            PartnerPosition pos = s.position(p.id());
            positions.add(new PositionView(p.id().value().toString(), p.name(), amt(pos.basis()),
                    amt(pos.balance()), amt(pos.payable()), amt(pos.liability())));
        }
        return new SyirkahView(summary(SyirkahSummary.of(s)), contractView(s.contract()), positions);
    }

    static CloseView close(CloseSnapshot c) {
        return new CloseView(c.id().value().toString(), c.sequence(), c.periodStart(), c.periodEnd(),
                c.contractVersion(), amt(c.netResult()), c.disposition().name(), c.status().name(),
                c.supersedes() == null ? null : c.supersedes().value().toString(), c.closedAt(), c.hash());
    }

    static ApprovalView approval(PendingApproval p) {
        return new ApprovalView(p.id().toString(), p.syirkahId().value().toString(), p.kind().name(), p.maker(),
                p.proposedAt(), p.status().name(), p.checker(), p.decidedAt(), p.note());
    }

    static LiquidationView liquidationView(LiquidationResult r) {
        return new LiquidationView(amt(r.residual()), amt(r.netResult()), r.payouts().stream()
                .map(p -> new PayoutView(p.partner().value().toString(), amt(p.cashReceived()), amt(p.owed())))
                .toList(), r.hash());
    }
}
