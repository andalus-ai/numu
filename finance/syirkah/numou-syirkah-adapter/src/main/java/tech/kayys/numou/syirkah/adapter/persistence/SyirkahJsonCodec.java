package tech.kayys.numou.numouadapter.persistence;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.numouapplication.command.Commands;
import tech.kayys.numou.numouapplication.governance.PendingApproval;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.numoudomain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Lossless JSON mapping for the documents stored as jsonb: contract versions,
 * closes, liquidation. Amounts are
 * written as plain decimals (scale preserved) and read as BigDecimal. Decoding
 * is strict: a missing or mistyped
 * field fails loudly, and the aggregate's own hash/integrity checks run
 * afterwards in {@code Syirkah.restore}.
 */
public final class SyirkahJsonCodec {

    private SyirkahJsonCodec() {
    }

    // ------------------------------------------------------------ encode

    public static String encodeContract(SyirkahContract c) {
        return Json.write(contract(c));
    }

    public static String encodeClose(CloseSnapshot c) {
        return Json.write(close(c));
    }

    public static String encodeLiquidation(LiquidationResult r) {
        return Json.write(liquidation(r));
    }

    public static String encodeFormation(AkadFormation f) {
        Map<String, Object> o = new LinkedHashMap<>();
        List<Object> parties = new ArrayList<>();
        for (PartyRecord p : f.parties()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("partner", p.partner().value().toString());
            m.put("dateOfBirth", p.dateOfBirth() == null ? null : p.dateOfBirth().toString());
            m.put("residence", p.residence());
            m.put("soundMind", p.soundMind());
            m.put("authorityOverCapital", p.authorityOverCapital());
            m.put("freeConsent", p.freeConsent());
            m.put("ijabQabulStatement", p.ijabQabulStatement());
            parties.add(m);
        }
        o.put("parties", parties);
        o.put("businessActivity", f.businessActivity());
        AkadFormation.LawfulnessReview r = f.lawfulnessReview();
        if (r == null) {
            o.put("lawfulnessReview", null);
        } else {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reviewedBy", r.reviewedBy());
            m.put("reviewedOn", r.reviewedOn() == null ? null : r.reviewedOn().toString());
            m.put("lawful", r.lawful());
            o.put("lawfulnessReview", m);
        }
        o.put("combinedAkad", new ArrayList<Object>(new TreeSet<>(f.combinedAkad())));
        o.put("unresolvedAmbiguities", new ArrayList<Object>(f.unresolvedAmbiguities()));
        o.put("sameSession", f.sameSession());
        o.put("ijabWithdrawn", f.ijabWithdrawn());
        List<Object> sections = new ArrayList<>();
        f.sectionsPresent().stream().sorted().forEach(x -> sections.add(x.name()));
        o.put("sectionsPresent", sections);
        o.put("signedPlace", f.signedPlace());
        o.put("signedOn", f.signedOn() == null ? null : f.signedOn().toString());
        List<Object> witnesses = new ArrayList<>();
        for (Witness w : f.witnesses()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", w.name());
            m.put("sex", w.sex() == null ? null : w.sex().name());
            witnesses.add(m);
        }
        o.put("witnesses", witnesses);
        return Json.write(o);
    }

    public static String encodeLifecycle(LifecycleChange ch) {
        return Json.write(lifecycle(ch));
    }

    private static Map<String, Object> lifecycle(LifecycleChange ch) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("kind", ch.kind().name());
        List<Object> affected = new ArrayList<>();
        ch.affected().stream().map(x -> x.value().toString()).sorted().forEach(affected::add);
        o.put("affected", affected);
        o.put("eventDate", ch.eventDate().toString());
        List<Object> consents = new ArrayList<>();
        for (LifecycleConsent c : ch.consents()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("partner", c.partner().value().toString());
            m.put("approved", c.approved());
            m.put("decidedOn", c.decidedOn().toString());
            m.put("evidence", c.evidence());
            consents.add(m);
        }
        o.put("consents", consents);
        o.put("profitDecision", ch.profitDecision() == null ? null : ch.profitDecision().name());
        List<Object> syufah = new ArrayList<>();
        for (SyufahResponse r : ch.syufah()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("partner", r.partner().value().toString());
            m.put("exercised", r.exercised());
            m.put("answeredOn", r.answeredOn().toString());
            syufah.add(m);
        }
        o.put("syufah", syufah);
        List<Object> successions = new ArrayList<>();
        for (Succession s : ch.successions()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deceased", s.deceased().value().toString());
            m.put("heir", s.heir().value().toString());
            m.put("continues", s.continues());
            m.put("decidedOn", s.decidedOn().toString());
            m.put("evidence", s.evidence());
            successions.add(m);
        }
        o.put("successions", successions);
        return o;
    }

    public static LifecycleChange decodeLifecycle(String json) {
        return lifecycle(obj(Json.read(json), "lifecycle"));
    }

    private static LifecycleChange lifecycle(Map<String, Object> o) {
        Set<PartnerId> affected = new HashSet<>();
        for (Object x : list(o, "affected")) {
            affected.add(pid((String) x));
        }
        List<LifecycleConsent> consents = new ArrayList<>();
        for (Object x : list(o, "consents")) {
            Map<String, Object> m = obj(x, "consent");
            consents.add(new LifecycleConsent(pid(str(m, "partner")), bool(m, "approved"),
                    LocalDate.parse(str(m, "decidedOn")), str(m, "evidence")));
        }
        String pd = optStr(o, "profitDecision");
        List<SyufahResponse> syufah = new ArrayList<>();
        for (Object x : list(o, "syufah")) {
            Map<String, Object> m = obj(x, "syufah");
            syufah.add(new SyufahResponse(pid(str(m, "partner")), bool(m, "exercised"),
                    LocalDate.parse(str(m, "answeredOn"))));
        }
        List<Succession> successions = new ArrayList<>();
        for (Object x : list(o, "successions")) {
            Map<String, Object> m = obj(x, "succession");
            successions.add(new Succession(pid(str(m, "deceased")), pid(str(m, "heir")), bool(m, "continues"),
                    LocalDate.parse(str(m, "decidedOn")), str(m, "evidence")));
        }
        return new LifecycleChange(LifecycleKind.valueOf(str(o, "kind")), affected,
                LocalDate.parse(str(o, "eventDate")), consents, pd == null ? null : ProfitDisposition.valueOf(pd),
                syufah, successions);
    }

    public static String encodeStaff(StaffContract st) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("employer", st.employer().name());
        o.put("employeePartner", st.employeePartner() == null ? null : st.employeePartner().value().toString());
        o.put("workType", st.workType());
        o.put("workPeriod", st.workPeriod());
        o.put("effort", st.effort());
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("basis", st.wage().basis().name());
        w.put("fixedAmount", st.wage().fixedAmount() == null ? null : money(st.wage().fixedAmount()));
        w.put("payPeriod", st.wage().payPeriod());
        o.put("wage", w);
        o.put("jaliClauses", new ArrayList<Object>(st.jaliClauses()));
        o.put("jaliReviewedLawful", st.jaliReviewedLawful());
        return Json.write(o);
    }

    public static StaffContract decodeStaff(String json) {
        Map<String, Object> o = obj(Json.read(json), "staff");
        Map<String, Object> w = obj(req(o, "wage"), "wage");
        String emp = optStr(o, "employeePartner");
        List<String> clauses = new ArrayList<>();
        for (Object x : list(o, "jaliClauses")) {
            clauses.add((String) x);
        }
        return new StaffContract(StaffContract.Employer.valueOf(str(o, "employer")), emp == null ? null : pid(emp),
                optStr(o, "workType"), optStr(o, "workPeriod"), optStr(o, "effort"),
                new StaffContract.Wage(StaffContract.WageBasis.valueOf(str(w, "basis")),
                        w.get("fixedAmount") == null ? null : money(w, "fixedAmount"), optStr(w, "payPeriod")),
                clauses, bool(o, "jaliReviewedLawful"));
    }

    private static final String[] TRADING_FIELDS = {"sales", "salesReturns", "salesDiscounts", "openingStock",
            "purchases", "purchaseReturns", "purchaseDiscounts", "closingStock", "operatingCosts", "otherCosts"};

    public static String encodeTrading(TradingResultCalculator.Input in) {
        Money[] v = {in.sales(), in.salesReturns(), in.salesDiscounts(), in.openingStock(), in.purchases(),
                in.purchaseReturns(), in.purchaseDiscounts(), in.closingStock(), in.operatingCosts(),
                in.otherCosts()};
        Map<String, Object> o = new LinkedHashMap<>();
        for (int i = 0; i < v.length; i++) {
            o.put(TRADING_FIELDS[i], money(v[i]));
        }
        return Json.write(o);
    }

    public static TradingResultCalculator.Input decodeTrading(String json) {
        Map<String, Object> o = obj(Json.read(json), "trading");
        Money[] m = new Money[TRADING_FIELDS.length];
        for (int i = 0; i < m.length; i++) {
            m[i] = money(o, TRADING_FIELDS[i]);
        }
        return new TradingResultCalculator.Input(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9]);
    }

    public static String encodePolicy(FormationPolicy p) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("witnessRule", p.witnessRule().name());
        o.put("blockMultiAkad", p.blockMultiAkad());
        o.put("minimumAgeYears", p.minimumAgeYears() == null ? null : (long) p.minimumAgeYears());
        return Json.write(o);
    }

    private static Map<String, Object> money(Money m) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("amount", m.amount());
        o.put("currency", m.currency().code());
        return o;
    }

    private static Map<String, Object> contract(SyirkahContract c) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("version", (long) c.version());
        o.put("type", c.type().name());
        o.put("currency", c.currency().code());
        List<Object> partners = new ArrayList<>();
        for (Partner p : c.partners()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id().value().toString());
            m.put("name", p.name());
            m.put("role", p.role().name());
            m.put("contributesWork", p.contributesWork());
            partners.add(m);
        }
        o.put("partners", partners);
        List<Object> capital = new ArrayList<>();
        for (CapitalContribution k : c.capital()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("partner", k.partner().value().toString());
            m.put("kind", k.kind().name());
            m.put("amount", money(k.amount()));
            m.put("valuationNote", k.valuationNote());
            capital.add(m);
        }
        o.put("capital", capital);
        Map<String, Object> nisbah = new LinkedHashMap<>();
        c.nisbah().shares().forEach((p, v) -> nisbah.put(p.value().toString(), v));
        o.put("nisbah", nisbah);
        o.put("profitBasis", c.profitBasis().name());
        Map<String, Object> loss = new LinkedHashMap<>();
        c.lossSharing().forEach((p, v) -> loss.put(p.value().toString(), v));
        o.put("lossSharing", loss);
        List<Object> clauses = new ArrayList<>();
        c.declaredClauses().stream().sorted().forEach(x -> clauses.add(x.name()));
        o.put("declaredClauses", clauses);
        o.put("witnesses", new ArrayList<Object>(c.witnesses()));
        o.put("ijabQabulRecorded", c.ijabQabulRecorded());
        o.put("effectiveFrom", c.effectiveFrom().toString());
        return o;
    }

    private static Map<String, Object> line(AllocationLine l) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("partner", l.partner().value().toString());
        o.put("capitalRestored", money(l.capitalRestored()));
        o.put("profitShare", money(l.profitShare()));
        o.put("lossBorne", money(l.lossBorne()));
        o.put("managerLiability", money(l.managerLiability()));
        o.put("retained", money(l.retained()));
        return o;
    }

    private static Map<String, Object> close(CloseSnapshot c) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", c.id().value().toString());
        o.put("sequence", (long) c.sequence());
        o.put("periodStart", c.periodStart().toString());
        o.put("periodEnd", c.periodEnd().toString());
        o.put("contractVersion", (long) c.contractVersion());
        o.put("contractCanonical", c.contractCanonical());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("revenue", money(c.result().revenue()));
        r.put("directCosts", money(c.result().directCosts()));
        r.put("operatingCosts", money(c.result().operatingCosts()));
        r.put("otherCosts", money(c.result().otherCosts()));
        o.put("result", r);
        o.put("disposition", c.disposition().name());
        o.put("fault", fault(c.fault()));
        List<Object> lines = new ArrayList<>();
        c.lines().forEach(l -> lines.add(line(l)));
        o.put("lines", lines);
        o.put("supersedes", c.supersedes() == null ? null : c.supersedes().value().toString());
        o.put("status", c.status().name());
        o.put("firstMovementSequence", c.firstMovementSequence());
        o.put("lastMovementSequence", c.lastMovementSequence());
        o.put("closedAt", c.closedAt().toString());
        o.put("previousHash", c.previousHash());
        o.put("hash", c.hash());
        return o;
    }

    private static Map<String, Object> fault(ManagerFault f) {
        if (f == null) {
            return null;
        }
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("negligentOrBreach", f.negligentOrBreach());
        o.put("attributableLoss", f.attributableLoss() == null ? null : money(f.attributableLoss()));
        o.put("reason", f.reason());
        return o;
    }

    private static Map<String, Object> liquidation(LiquidationResult r) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("input", liquidationInput(r.input()));
        o.put("residual", money(r.residual()));
        o.put("netResult", money(r.netResult()));
        List<Object> lines = new ArrayList<>();
        r.lines().forEach(l -> lines.add(line(l)));
        o.put("lines", lines);
        List<Object> payouts = new ArrayList<>();
        for (LiquidationResult.Payout p : r.payouts()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("partner", p.partner().value().toString());
            m.put("cashReceived", money(p.cashReceived()));
            m.put("owed", money(p.owed()));
            payouts.add(m);
        }
        o.put("payouts", payouts);
        o.put("completedAt", r.completedAt().toString());
        o.put("hash", r.hash());
        return o;
    }

    // ------------------------------------------------------------ decode

    public static SyirkahContract decodeContract(String json) {
        return contract(obj(Json.read(json), "contract"));
    }

    public static CloseSnapshot decodeClose(String json) {
        return close(obj(Json.read(json), "close"));
    }

    public static LiquidationResult decodeLiquidation(String json) {
        return liquidation(obj(Json.read(json), "liquidation"));
    }

    public static AkadFormation decodeFormation(String json) {
        Map<String, Object> o = obj(Json.read(json), "formation");
        List<PartyRecord> parties = new ArrayList<>();
        for (Object x : list(o, "parties")) {
            Map<String, Object> m = obj(x, "party");
            String dob = optStr(m, "dateOfBirth");
            parties.add(new PartyRecord(pid(str(m, "partner")), dob == null ? null : LocalDate.parse(dob),
                    optStr(m, "residence"), bool(m, "soundMind"), bool(m, "authorityOverCapital"),
                    bool(m, "freeConsent"), optStr(m, "ijabQabulStatement")));
        }
        AkadFormation.LawfulnessReview review = null;
        if (req(o, "lawfulnessReview") != null) {
            Map<String, Object> m = obj(o.get("lawfulnessReview"), "lawfulnessReview");
            String on = optStr(m, "reviewedOn");
            review = new AkadFormation.LawfulnessReview(optStr(m, "reviewedBy"), on == null ? null : LocalDate.parse(on),
                    bool(m, "lawful"));
        }
        Set<String> combined = new TreeSet<>();
        for (Object x : list(o, "combinedAkad")) {
            combined.add((String) x);
        }
        List<String> ambiguities = new ArrayList<>();
        for (Object x : list(o, "unresolvedAmbiguities")) {
            ambiguities.add((String) x);
        }
        Set<AkadSection> sections = EnumSet.noneOf(AkadSection.class);
        for (Object x : list(o, "sectionsPresent")) {
            sections.add(AkadSection.valueOf((String) x));
        }
        String signedOn = optStr(o, "signedOn");
        List<Witness> witnesses = new ArrayList<>();
        for (Object x : list(o, "witnesses")) {
            Map<String, Object> m = obj(x, "witness");
            String sex = optStr(m, "sex");
            witnesses.add(new Witness(str(m, "name"), sex == null ? null : WitnessSex.valueOf(sex)));
        }
        return new AkadFormation(parties, optStr(o, "businessActivity"), review, combined, ambiguities,
                bool(o, "sameSession"), bool(o, "ijabWithdrawn"), sections, optStr(o, "signedPlace"),
                signedOn == null ? null : LocalDate.parse(signedOn), witnesses);
    }

    public static FormationPolicy decodePolicy(String json) {
        Map<String, Object> o = obj(Json.read(json), "policy");
        Object years = req(o, "minimumAgeYears");
        return new FormationPolicy(WitnessRule.valueOf(str(o, "witnessRule")), bool(o, "blockMultiAkad"),
                years == null ? null : ((BigDecimal) years).intValueExact());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Object v, String what) {
        if (!(v instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(what + ": expected object");
        }
        return (Map<String, Object>) v;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> o, String k) {
        Object v = req(o, k);
        if (!(v instanceof List<?>)) {
            throw new IllegalArgumentException(k + ": expected array");
        }
        return (List<Object>) v;
    }

    private static Object req(Map<String, Object> o, String k) {
        if (!o.containsKey(k)) {
            throw new IllegalArgumentException("missing field " + k);
        }
        return o.get(k);
    }

    private static String str(Map<String, Object> o, String k) {
        Object v = req(o, k);
        if (!(v instanceof String s)) {
            throw new IllegalArgumentException(k + ": expected string");
        }
        return s;
    }

    private static String optStr(Map<String, Object> o, String k) {
        Object v = req(o, k);
        if (v == null) {
            return null;
        }
        if (!(v instanceof String s)) {
            throw new IllegalArgumentException(k + ": expected string");
        }
        return s;
    }

    private static boolean bool(Map<String, Object> o, String k) {
        if (!(req(o, k) instanceof Boolean b)) {
            throw new IllegalArgumentException(k + ": expected boolean");
        }
        return b;
    }

    private static BigDecimal num(Object v, String k) {
        if (!(v instanceof BigDecimal d)) {
            throw new IllegalArgumentException(k + ": expected number");
        }
        return d;
    }

    private static long lng(Map<String, Object> o, String k) {
        return num(req(o, k), k).longValueExact();
    }

    private static Money money(Map<String, Object> o, String k) {
        Map<String, Object> m = obj(req(o, k), k);
        return new Money(num(req(m, "amount"), "amount"), Currency.of(str(m, "currency")));
    }

    private static PartnerId pid(String s) {
        return PartnerId.of(s);
    }

    private static SyirkahContract contract(Map<String, Object> o) {
        List<Partner> partners = new ArrayList<>();
        for (Object x : list(o, "partners")) {
            Map<String, Object> m = obj(x, "partner");
            partners.add(new Partner(pid(str(m, "id")), str(m, "name"), PartnerRole.valueOf(str(m, "role")),
                    bool(m, "contributesWork")));
        }
        List<CapitalContribution> capital = new ArrayList<>();
        for (Object x : list(o, "capital")) {
            Map<String, Object> m = obj(x, "capital");
            capital.add(new CapitalContribution(pid(str(m, "partner")), CapitalKind.valueOf(str(m, "kind")),
                    money(m, "amount"), optStr(m, "valuationNote")));
        }
        Map<PartnerId, BigDecimal> nisbah = new LinkedHashMap<>();
        obj(req(o, "nisbah"), "nisbah").forEach((k, v) -> nisbah.put(pid(k), num(v, "nisbah")));
        Map<PartnerId, BigDecimal> loss = new LinkedHashMap<>();
        obj(req(o, "lossSharing"), "lossSharing").forEach((k, v) -> loss.put(pid(k), num(v, "lossSharing")));
        Set<ForbiddenClause> clauses = new HashSet<>();
        for (Object x : list(o, "declaredClauses")) {
            clauses.add(ForbiddenClause.valueOf((String) x));
        }
        List<String> witnesses = new ArrayList<>();
        for (Object x : list(o, "witnesses")) {
            witnesses.add((String) x);
        }
        return new SyirkahContract((int) lng(o, "version"), SyirkahType.valueOf(str(o, "type")),
                Currency.of(str(o, "currency")), partners, capital, ProfitSharingRatio.ofFractions(nisbah),
                ProfitBasis.valueOf(str(o, "profitBasis")), loss, clauses, witnesses,
                bool(o, "ijabQabulRecorded"), LocalDate.parse(str(o, "effectiveFrom")));
    }

    private static List<AllocationLine> lines(Map<String, Object> o) {
        List<AllocationLine> out = new ArrayList<>();
        for (Object x : list(o, "lines")) {
            Map<String, Object> m = obj(x, "line");
            out.add(new AllocationLine(pid(str(m, "partner")), money(m, "capitalRestored"),
                    money(m, "profitShare"), money(m, "lossBorne"), money(m, "managerLiability"),
                    money(m, "retained")));
        }
        return out;
    }

    private static CloseSnapshot close(Map<String, Object> o) {
        Map<String, Object> r = obj(req(o, "result"), "result");
        FinancialResult result = new FinancialResult(money(r, "revenue"), money(r, "directCosts"),
                money(r, "operatingCosts"), money(r, "otherCosts"));
        Object f = req(o, "fault");
        ManagerFault fault = null;
        if (f != null) {
            Map<String, Object> m = obj(f, "fault");
            fault = new ManagerFault(bool(m, "negligentOrBreach"),
                    req(m, "attributableLoss") == null ? null : money(m, "attributableLoss"),
                    optStr(m, "reason"));
        }
        String sup = optStr(o, "supersedes");
        return new CloseSnapshot(new CloseId(UUID.fromString(str(o, "id"))), (int) lng(o, "sequence"),
                LocalDate.parse(str(o, "periodStart")), LocalDate.parse(str(o, "periodEnd")),
                (int) lng(o, "contractVersion"), str(o, "contractCanonical"), result,
                ProfitDisposition.valueOf(str(o, "disposition")), fault, lines(o),
                sup == null ? null : new CloseId(UUID.fromString(sup)), CloseStatus.valueOf(str(o, "status")),
                lng(o, "firstMovementSequence"), lng(o, "lastMovementSequence"),
                Instant.parse(str(o, "closedAt")), str(o, "previousHash"), str(o, "hash"));
    }

    private static LiquidationResult liquidation(Map<String, Object> o) {
        LiquidationInput input = liquidationInput(obj(req(o, "input"), "input"));
        List<LiquidationResult.Payout> payouts = new ArrayList<>();
        for (Object x : list(o, "payouts")) {
            Map<String, Object> m = obj(x, "payout");
            payouts.add(new LiquidationResult.Payout(pid(str(m, "partner")), money(m, "cashReceived"),
                    money(m, "owed")));
        }
        return new LiquidationResult(input, money(o, "residual"), money(o, "netResult"), lines(o), payouts,
                Instant.parse(str(o, "completedAt")), str(o, "hash"));
    }

    // ------------------------------------------------------------ approval
    // payloads (maker-checker)

    /**
     * Encodes the command held by a pending approval; what the checker approves is
     * exactly what is replayed.
     */
    public static String encodePayload(PendingApproval.Kind kind, Object payload) {
        Map<String, Object> o = new LinkedHashMap<>();
        switch (kind) {
            case REVISE_CONTRACT -> {
                Commands.ReviseContract c = (Commands.ReviseContract) payload;
                o.put("next", contract(c.next()));
                List<Object> ids = new ArrayList<>();
                c.approvals().stream().sorted().forEach(p -> ids.add(p.value().toString()));
                o.put("approvals", ids);
                if (c.lifecycle() != null) {
                    o.put("lifecycle", lifecycle(c.lifecycle()));
                }
            }
            case CORRECT_CLOSE -> {
                Commands.CorrectClose c = (Commands.CorrectClose) payload;
                o.put("target", c.target().value().toString());
                o.put("replacement", c.replacement().value().toString());
                o.put("corrected", financialResult(c.corrected()));
                o.put("fault", fault(c.fault()));
                o.put("disposition", c.disposition().name());
                List<Object> later = new ArrayList<>();
                c.laterReplacements().forEach(r -> later.add(r.value().toString()));
                o.put("laterReplacements", later);
            }
            case START_LIQUIDATION -> {
                // no parameters
            }
            case LIQUIDATE -> {
                Commands.Liquidate c = (Commands.Liquidate) payload;
                o.put("input", liquidationInput(c.input()));
                o.put("fault", fault(c.fault()));
            }
        }
        return Json.write(o);
    }

    public static Object decodePayload(PendingApproval.Kind kind, TenantId tenant, SyirkahId id, String json) {
        Map<String, Object> o = obj(Json.read(json), "payload");
        switch (kind) {
            case REVISE_CONTRACT -> {
                Set<PartnerId> approvals = new TreeSet<>();
                for (Object x : list(o, "approvals")) {
                    approvals.add(pid((String) x));
                }
                LifecycleChange lc = o.get("lifecycle") == null ? null
                        : lifecycle(obj(o.get("lifecycle"), "lifecycle"));
                return new Commands.ReviseContract(tenant, id, contract(obj(req(o, "next"), "next")), approvals, lc);
            }
            case CORRECT_CLOSE -> {
                return new Commands.CorrectClose(tenant, id, new CloseId(UUID.fromString(str(o, "target"))),
                        new CloseId(UUID.fromString(str(o, "replacement"))),
                        financialResult(obj(req(o, "corrected"), "corrected")), faultOrNull(o),
                        ProfitDisposition.valueOf(str(o, "disposition")), laterReplacements(o));
            }
            case START_LIQUIDATION -> {
                return new Commands.StartLiquidation(tenant, id);
            }
            case LIQUIDATE -> {
                return new Commands.Liquidate(tenant, id, liquidationInput(obj(req(o, "input"), "input")),
                        faultOrNull(o));
            }
            default -> throw new IllegalArgumentException("unknown approval kind " + kind);
        }
    }

    /** Absent in payloads written before cascade corrections existed. */
    private static List<CloseId> laterReplacements(Map<String, Object> o) {
        List<CloseId> out = new ArrayList<>();
        if (o.containsKey("laterReplacements")) {
            for (Object x : list(o, "laterReplacements")) {
                out.add(new CloseId(UUID.fromString((String) x)));
            }
        }
        return out;
    }

    private static Map<String, Object> financialResult(FinancialResult r) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("revenue", money(r.revenue()));
        o.put("directCosts", money(r.directCosts()));
        o.put("operatingCosts", money(r.operatingCosts()));
        o.put("otherCosts", money(r.otherCosts()));
        return o;
    }

    private static FinancialResult financialResult(Map<String, Object> r) {
        return new FinancialResult(money(r, "revenue"), money(r, "directCosts"), money(r, "operatingCosts"),
                money(r, "otherCosts"));
    }

    private static Map<String, Object> liquidationInput(LiquidationInput i) {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("assetProceeds", money(i.assetProceeds()));
        in.put("externalDebts", money(i.externalDebts()));
        in.put("shufahOffered", i.shufahOffered());
        in.put("liquidationDate", i.liquidationDate().toString());
        if (!i.sales().isEmpty()) { // omitted when empty so documents written before asset sales stay byte-identical
            List<Object> sales = new ArrayList<>();
            for (AssetSale sale : i.sales()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("description", sale.description());
                m.put("proceeds", money(sale.proceeds()));
                m.put("buyer", sale.buyer().name());
                m.put("buyerPartner", sale.buyerPartner() == null ? null : sale.buyerPartner().value().toString());
                m.put("shufahOffered", sale.shufahOffered());
                sales.add(m);
            }
            in.put("sales", sales);
        }
        return in;
    }

    private static LiquidationInput liquidationInput(Map<String, Object> in) {
        List<AssetSale> sales = new ArrayList<>();
        if (in.containsKey("sales")) {
            for (Object x : list(in, "sales")) {
                Map<String, Object> m = obj(x, "sale");
                String buyer = optStr(m, "buyerPartner");
                sales.add(new AssetSale(str(m, "description"), money(m, "proceeds"),
                        AssetSale.Buyer.valueOf(str(m, "buyer")), buyer == null ? null : pid(buyer),
                        bool(m, "shufahOffered")));
            }
        }
        return new LiquidationInput(money(in, "assetProceeds"), money(in, "externalDebts"),
                bool(in, "shufahOffered"), LocalDate.parse(str(in, "liquidationDate")), sales);
    }

    private static ManagerFault faultOrNull(Map<String, Object> o) {
        Object f = req(o, "fault");
        if (f == null) {
            return null;
        }
        Map<String, Object> m = obj(f, "fault");
        return new ManagerFault(bool(m, "negligentOrBreach"),
                req(m, "attributableLoss") == null ? null : money(m, "attributableLoss"), optStr(m, "reason"));
    }
}
