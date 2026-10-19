package tech.kayys.numou.budget.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A replace-by-key, auditable snapshot of budget lines. */
public final class BudgetVersion {
    private final int number;
    private final List<BudgetLine> lines;

    public BudgetVersion(int number, List<BudgetLine> lines) {
        if (number < 1) throw new IllegalArgumentException("version number must be positive");
        this.number = number;
        this.lines = new ArrayList<>(List.copyOf(lines));
    }
    public int number() { return number; }
    public List<BudgetLine> lines() { return List.copyOf(lines); }
    public void addOrReplace(BudgetLine line) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).matches(line.accountCode(), line.dimensions(), line.period())) {
                lines.set(i, line);
                return;
            }
        }
        lines.add(line);
    }
    public BigDecimal totalFor(String accountCode, Map<String, String> dimensions, BudgetPeriod period) {
        return lines.stream()
                .filter(line -> line.status() == BudgetLineStatus.ACTIVE
                        && line.matches(accountCode, dimensions, period))
                .map(BudgetLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public BigDecimal total() {
        return lines.stream().filter(line -> line.status() == BudgetLineStatus.ACTIVE)
                .map(BudgetLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public BudgetVersion copyAs(int nextNumber) {
        return new BudgetVersion(nextNumber, lines);
    }
}
