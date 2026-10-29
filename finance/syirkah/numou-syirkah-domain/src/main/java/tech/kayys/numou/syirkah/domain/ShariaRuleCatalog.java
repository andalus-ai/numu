package tech.kayys.numou.numoudomain;

import tech.kayys.numou.numoudomain.ShariaRule.Enforcement;
import tech.kayys.numou.numoudomain.ShariaRule.Ruling;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Every Sharia rule Numou enforces, in one place that can be read from top to bottom.
 *
 * <p>The rules themselves stay in Java, next to the money they protect. This catalog does not run them; it names
 * them: where each comes from, which finding codes enforce it, and which tests prove it. Three tests keep it honest:
 * every code the checkers can emit appears here (no undocumented rule), every code listed here is produced by the
 * conformance scenarios (no rule that is never exercised), and the generated document
 * {@code doc/SHARIA-RULES.md} matches this catalog exactly (so a change to a rule shows in review).
 *
 * <p><b>Freezing.</b> When the adviser has ruled and the conformance suite passes, fill in {@link #ADVISER_SIGNOFF}
 * and {@link #FROZEN_FINGERPRINT}. From then on a test fails if any rule changes without both being updated.
 */
public final class ShariaRuleCatalog {

    /** Who signed off, and when; null until the adviser has ruled. */
    public static final String ADVISER_SIGNOFF = null;

    /** The fingerprint the catalog had when it was frozen; null while the rules can still change. */
    public static final String FROZEN_FINGERPRINT = null;

    private static final String H = "Hukum Muamalah ";

    private static final List<ShariaRule> RULES = List.of(
            rule(1, "Profit is split by the agreed nisbah on net profit, never on revenue", "Bab XIV, XXI",
                    Enforcement.BLOCKS, Ruling.BOOK_STATED, List.of("PROFIT_ON_REVENUE"), List.of(), ""),
            rule(2, "The nisbah totals 100% and names every partner", "Bab XIV, XXI", Enforcement.BLOCKS,
                    Ruling.BOOK_STATED, List.of("NISBAH_PARTNERS"), List.of(), ""),
            rule(3, "Profit exists only once capital is intact", "Bab XXI", Enforcement.BEHAVIOUR,
                    Ruling.BOOK_STATED, List.of(), List.of("SyirkahTest"),
                    "Capital is restored before any profit is split."),
            rule(4, "Loss follows capital proportion", "Bab XV, XIV principle 10", Enforcement.BLOCKS,
                    Ruling.PENDING_ADVISER,
                    List.of("LOSS_NOT_BY_CAPITAL", "LOSS_SHARING_INVALID", "LOSS_SHARING_SUM"), List.of(),
                    "Explicit loss shares are refused for the types that need cash capital. Abdan and wujuh loss "
                            + "models wait for the adviser."),
            rule(5, "The akad needs ijab and qabul, in one session, not withdrawn", "Bab X (in'iqad conditions 6, 7)",
                    Enforcement.BLOCKS, Ruling.BOOK_STATED,
                    List.of("IJAB_QABUL", "PARTY_IJAB_QABUL_MISSING", "SESSION_NOT_UNIFIED", "IJAB_WITHDRAWN"),
                    List.of(), ""),
            rule(6, "The akad is written and has named witnesses", "Bab XVIII", Enforcement.BLOCKS,
                    Ruling.BOOK_STATED, List.of("WITNESSES", "WITNESS_NAME_BLANK", "WITNESS_MISMATCH"), List.of(), ""),
            rule(7, "Barred terms make the akad invalid", "Bab X, XV", Enforcement.BLOCKS, Ruling.PENDING_ADVISER,
                    List.of("FORBIDDEN_*"), List.of(),
                    "Only six clauses are blocked: fixed profit amount, capital guarantee, manager salary, loss not by "
                            + "capital, capital provider control, profit exclusion. The adviser should supply the "
                            + "full list."),
            rule(8, "Capital, work and roles fit the type of syirkah", "Bab XV", Enforcement.BLOCKS,
                    Ruling.PENDING_ADVISER,
                    List.of("PARTNERS_MIN", "PARTNER_DUPLICATE", "CAPITAL_REQUIRED", "CAPITAL_VALUATION",
                            "CAPITAL_UNKNOWN_PARTNER", "NO_CONTRIBUTION", "NO_CASH_CAPITAL", "MUDHARABAH_ROLES",
                            "MUDHARABAH_MANAGER_CAPITAL", "MUDHARABAH_MANAGER_WORK", "MUDHARABAH_PROVIDER_CAPITAL",
                            "ABDAN_WORK", "ROLE_MISMATCH", "MUFAWADHAH_EQUAL_CAPITAL", "MUFAWADHAH_EQUAL_WORK"),
                    List.of(),
                    "Goods are allowed as capital when valued at the akad. The equal-capital check for mufawadhah is "
                            + "the Hanafi rule; the book defines mufawadhah as a combination of the other types."),
            rule(9, "Changed terms need closed books, every partner's consent and a new akad",
                    "Bab XIV principle 8, XXIV, XXV", Enforcement.BLOCKS, Ruling.BOOK_STATED,
                    List.of("NOT_ACTIVE", "NEW_AKAD_VERSION", "BOOKS_NOT_CLOSED", "CONSENT_MISSING", "DISSENT"),
                    List.of(), "Consents carry evidence; a dissent is kept and blocks the change."),
            rule(10, "The mudharib bears loss only for negligence or breach", "Bab XV", Enforcement.BEHAVIOUR,
                    Ruling.BOOK_STATED, List.of(), List.of("SyirkahTest"),
                    "The evidence workflow for fault lives in the accounting module."),
            rule(11, "Liquidation: sell, pay debts, then partners' claims", "Bab XXVI", Enforcement.BEHAVIOUR,
                    Ruling.BOOK_STATED, List.of(), List.of("SyirkahTest"),
                    "Assets are treated as already sold for cash; hak syuf'ah is checked per asset sale."),
            rule(12, "Statements: balance sheet and profit and loss in the book's layout", "Bab XIX",
                    Enforcement.BEHAVIOUR, Ruling.BOOK_STATED, List.of(), List.of("BookStatementsTest"),
                    "The caller supplies assets and outside debts; the sheet shows any difference."),
            rule(13, "Retained profit becomes partner capital", "Bab XXIII", Enforcement.BEHAVIOUR,
                    Ruling.BOOK_STATED, List.of(), List.of("SyirkahTest"), ""),
            rule(14, "Each party is fit to contract: of age, of sound mind, consenting, with authority over the capital",
                    "Bab X (in'iqad condition 1), XVIII D.1", Enforcement.RECORDED, Ruling.BOOK_STATED,
                    List.of("PARTY_RECORD_DUPLICATE", "PARTY_RECORD_UNKNOWN", "PARTY_RECORD_MISSING",
                            "PARTY_DOB_MISSING", "PARTY_DOB_INVALID", "PARTY_BELOW_MINIMUM_AGE",
                            "PARTY_RESIDENCE_MISSING", "PARTY_NOT_OF_SOUND_MIND", "PARTY_NO_AUTHORITY_OVER_CAPITAL",
                            "PARTY_COERCED"),
                    List.of(), "Software cannot judge sound mind; it requires the determination to be recorded. No "
                            + "minimum age is invented unless the policy sets one."),
            rule(15, "The subject of the akad is lawful", "Bab X (in'iqad conditions 2, 3)", Enforcement.RECORDED,
                    Ruling.BOOK_STATED, List.of("OBJECT_NOT_STATED", "LAWFULNESS_REVIEW_MISSING", "OBJECT_NOT_LAWFUL"),
                    List.of(), "Someone must record the review; a negative result blocks."),
            rule(16, "Void (batil) and defective (fasad) akad are different outcomes", "Bab X",
                    Enforcement.BEHAVIOUR, Ruling.BOOK_STATED, List.of("AMBIGUOUS_TERMS"),
                    List.of("AkadFormationCheckerTest"),
                    "Every finding carries its class: batil, fasad, incomplete or unclassified. Where the book does "
                            + "not say, it is not called batil."),
            rule(17, "Multi-akad is prohibited on the book's preferred view", "Bab XI", Enforcement.BLOCKS,
                    Ruling.PENDING_ADVISER, List.of("MULTI_AKAD"), List.of(),
                    "Scholars differ; the book prefers the view that it is haram. A policy setting turns it off."),
            rule(18, "Witnesses: two men, or one man and two women, or four women", "Bab XVIII",
                    Enforcement.BLOCKS, Ruling.PENDING_ADVISER, List.of("WITNESS_NISHAB"), List.of(),
                    "A plain count of witnesses is available as a policy."),
            rule(19, "The written akad carries its required parts", "Bab XVIII C.4, C.5", Enforcement.BLOCKS,
                    Ruling.BOOK_STATED, List.of("SECTION_MISSING_*", "SIGNING_PLACE_MISSING", "SIGNING_DATE_MISSING"),
                    List.of(), ""),
            rule(20, "Decisions need the managing partners' agreement; firm dissent ends the syirkah",
                    "Bab XIV principles 5 to 7", Enforcement.BLOCKS, Ruling.PENDING_ADVISER, List.of("DISSENT"),
                    List.of(), "Enforced for akad changes only. Day-to-day management decisions are not a ledger "
                            + "concern. Whether capital-only partners must consent to akad changes needs the adviser."),
            rule(21, "Staff are hired by the syirkah on a fixed wage, with clear work, period and effort",
                    "Bab XIV principle 10, XVI D, XVII", Enforcement.BLOCKS, Ruling.BOOK_STATED,
                    List.of("STAFF_HIRED_BY_PARTNER", "MANAGER_SALARY", "WORK_TYPE_MISSING", "WORK_PERIOD_MISSING",
                            "EFFORT_MISSING", "WAGE_NOT_FIXED", "WAGE_AMOUNT_MISSING", "WAGE_CURRENCY",
                            "PAY_PERIOD_MISSING", "JALI_REVIEW_MISSING"),
                    List.of(), "A pure check: staff contracts are not yet stored or exposed over REST."),
            rule(22, "A trading syirkah's cost of goods sold is opening stock plus purchases minus closing stock",
                    "Bab XXII", Enforcement.BEHAVIOUR, Ruling.BOOK_STATED, List.of(),
                    List.of("TradingResultCalculatorTest"),
                    "Periodic method only; lost goods sit inside the cost of goods sold."),
            rule(23, "New capital: close the books, then a new akad", "Bab XIV principle 8, XXIV",
                    Enforcement.BLOCKS, Ruling.BOOK_STATED,
                    List.of("PROFIT_DECISION_MISSING", "CAPITAL_REDUCED", "PARTNER_REMOVED", "NO_NEW_CAPITAL"),
                    List.of(), ""),
            rule(24, "Replacing a partner: every partner agrees, hak syuf'ah is offered, a new akad follows",
                    "Bab XXV, XXVI", Enforcement.BLOCKS, Ruling.BOOK_STATED,
                    List.of("NO_LEAVING_PARTNER", "NOT_A_PARTNER", "LEAVING_PARTNER_REMAINS", "SYUFAH_NOT_OFFERED",
                            "SYUFAH_EXERCISED"),
                    List.of(), ""),
            rule(25, "A partner's death ends the akad; the heir decides whether to continue in a new akad",
                    "Bab X (akad fasakh), XXIV", Enforcement.BLOCKS, Ruling.PENDING_ADVISER,
                    List.of("NO_DECEASED", "DECEASED_REMAINS", "SUCCESSION_UNDECIDED", "SUCCESSION_FOR_LIVING",
                            "HEIR_NOT_IN_AKAD", "HEIR_DECLINED_BUT_IN_AKAD", "BOOKS_NOT_CLOSED_TO_DEATH"),
                    List.of(), "The book says death ends the akad. Continuing through the heir is the owner's rule "
                            + "and needs the adviser's reading. The heir's capital is not one transfer in the ledger."),
            rule(26, "Muzara'ah and mukhabarah are haram; musaqah only on land that already has trees",
                    "Bab XV section H", Enforcement.BLOCKS, Ruling.BOOK_STATED,
                    List.of("BARRED_AGRICULTURE_AKAD", "MUSAQAH_NOT_PLANTED"), List.of(),
                    "Numou has no ledger type for agriculture; the name scan is a safeguard, not the lawfulness "
                            + "review."));

    private ShariaRuleCatalog() {
    }

    private static ShariaRule rule(int id, String title, String source, Enforcement e, Ruling r, List<String> codes,
                                   List<String> testedBy, String note) {
        return new ShariaRule(id, title, H + source, e, r, codes, testedBy, note);
    }

    public static List<ShariaRule> all() {
        return RULES;
    }

    /** True when some rule covers {@code code}. */
    public static boolean covers(String code) {
        return RULES.stream().anyMatch(r -> r.covers(code));
    }

    /** The catalog as a Markdown document: the same text every time for the same rules. */
    public static String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Sharia rules enforced by Numou syirkah\n\n");
        sb.append("Generated from `ShariaRuleCatalog`. Do not edit by hand: change the catalog and regenerate. "
                + "This is an implementation trace of one book, not a fatwa.\n\n");
        long pending = RULES.stream().filter(r -> r.ruling() == Ruling.PENDING_ADVISER).count();
        sb.append("- Rules: ").append(RULES.size()).append('\n');
        sb.append("- Waiting for the Sharia adviser: ").append(pending).append('\n');
        sb.append("- Adviser sign-off: ").append(ADVISER_SIGNOFF == null ? "none yet" : ADVISER_SIGNOFF).append('\n');
        sb.append("- Frozen: ").append(FROZEN_FINGERPRINT == null ? "no" : "yes").append("\n\n");
        for (ShariaRule r : RULES) {
            sb.append("## Rule ").append(r.id()).append(": ").append(r.title()).append("\n\n");
            sb.append("- Source: ").append(r.source()).append('\n');
            sb.append("- Enforcement: ").append(r.enforcement()).append('\n');
            sb.append("- Adviser: ").append(r.ruling()).append('\n');
            if (!r.codes().isEmpty()) {
                sb.append("- Finding codes: ").append(String.join(", ", r.codes())).append('\n');
            }
            if (!r.testedBy().isEmpty()) {
                sb.append("- Tests: ").append(String.join(", ", r.testedBy())).append('\n');
            }
            if (!r.note().isEmpty()) {
                sb.append("- Note: ").append(r.note()).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** SHA-256 of the rendered catalog, apart from the freeze lines themselves. */
    public static String fingerprint() {
        String text = render().replaceAll("- Adviser sign-off: .*\n", "").replaceAll("- Frozen: .*\n", "");
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
