package tech.kayys.numou.syirkah.adapter.persistence;

import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.syirkah.domain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Lossless JSON mapping for the documents stored as jsonb: contract versions, closes, liquidation. Amounts are
 * written as plain decimals (scale preserved) and read as BigDecimal. Decoding is strict: a missing or mistyped
 * field fails loudly, and the aggregate's own hash/integrity checks run afterwards in {@code Syirkah.restore}.
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
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("assetProceeds", money(r.input().assetProceeds()));
        in.put("externalDebts", money(r.input().externalDebts()));
        in.put("shufahOffered", r.input().shufahOffered());
        in.put("liquidationDate", r.input().liquidationDate().toString());
        o.put("input", in);
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
        Map<String, Object> in = obj(req(o, "input"), "input");
        LiquidationInput input = new LiquidationInput(money(in, "assetProceeds"), money(in, "externalDebts"),
                bool(in, "shufahOffered"), LocalDate.parse(str(in, "liquidationDate")));
        List<LiquidationResult.Payout> payouts = new ArrayList<>();
        for (Object x : list(o, "payouts")) {
            Map<String, Object> m = obj(x, "payout");
            payouts.add(new LiquidationResult.Payout(pid(str(m, "partner")), money(m, "cashReceived"),
                    money(m, "owed")));
        }
        return new LiquidationResult(input, money(o, "residual"), money(o, "netResult"), lines(o), payouts,
                Instant.parse(str(o, "completedAt")), str(o, "hash"));
    }
}
