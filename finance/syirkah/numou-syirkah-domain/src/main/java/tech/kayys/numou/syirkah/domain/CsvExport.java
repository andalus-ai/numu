package tech.kayys.numou.numoudomain;

import java.util.ArrayList;
import java.util.List;

/**
 * RFC 4180 CSV for statements and reports. Text cells that start with a formula
 * trigger (= + - @ tab CR) are
 * prefixed with an apostrophe so that opening the file in a spreadsheet cannot
 * execute a formula
 * (partner names and references are user-supplied). Numeric cells are written
 * as plain decimals.
 */
public final class CsvExport {

    private static final String EOL = "\r\n";

    private CsvExport() {
    }

    public static String statement(PartnerStatement st) {
        StringBuilder sb = new StringBuilder();
        row(sb, "sequence", "occurred_at", "type", "reference", "amount", "capital_balance", "profit_payable",
                "liability");
        for (StatementLine l : st.lines()) {
            row(sb, String.valueOf(l.sequence()), l.at().toString(), l.type().name(), text(l.reference()),
                    l.amount().amount().toPlainString(), l.capitalBalance().amount().toPlainString(),
                    l.profitPayable().amount().toPlainString(), l.liability().amount().toPlainString());
        }
        return sb.toString();
    }

    public static String closeReport(CloseReport r) {
        StringBuilder sb = new StringBuilder();
        row(sb, "partner", "capital_restored", "profit_share", "retained", "payable", "loss_borne",
                "manager_liability");
        for (CloseReport.Row w : r.rows()) {
            row(sb, text(w.name() == null ? w.partner().value().toString() : w.name()),
                    w.capitalRestored().amount().toPlainString(), w.profitShare().amount().toPlainString(),
                    w.retained().amount().toPlainString(), w.payable().amount().toPlainString(),
                    w.lossBorne().amount().toPlainString(), w.managerLiability().amount().toPlainString());
        }
        return sb.toString();
    }

    public static String rollForward(CapitalRollForward r) {
        StringBuilder sb = new StringBuilder();
        row(sb, "partner", "opening", "contributions", "returns", "retained_profit", "capital_restored",
                "losses_allocated", "deficit_reclass", "closing");
        for (CapitalRollForward.Row w : r.rows()) {
            row(sb, text(w.name() == null ? w.partner().value().toString() : w.name()),
                    w.opening().amount().toPlainString(), w.contributions().amount().toPlainString(),
                    w.returns().amount().toPlainString(), w.retainedProfit().amount().toPlainString(),
                    w.capitalRestored().amount().toPlainString(), w.lossesAllocated().amount().toPlainString(),
                    w.deficitReclass().amount().toPlainString(), w.closing().amount().toPlainString());
        }
        return sb.toString();
    }

    /** Neutralises formula injection in a text cell. */
    static String text(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        char c = s.charAt(0);
        return (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') ? "'" + s : s;
    }

    private static void row(StringBuilder sb, String... cells) {
        List<String> out = new ArrayList<>();
        for (String c : cells) {
            out.add(escape(c));
        }
        sb.append(String.join(",", out)).append(EOL);
    }

    private static String escape(String c) {
        if (c.indexOf(',') >= 0 || c.indexOf('"') >= 0 || c.indexOf('\n') >= 0 || c.indexOf('\r') >= 0) {
            return '"' + c.replace("\"", "\"\"") + '"';
        }
        return c;
    }
}
