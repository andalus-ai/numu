package tech.kayys.syirkah.accounting.application.posting;

import java.math.BigDecimal;
import java.util.List;

public record PostingInstruction(List<PostingLine> lines) {
    public PostingInstruction {
        lines = List.copyOf(lines);
        if (lines.isEmpty()) throw new IllegalArgumentException("posting must contain lines");
        var debits = total(lines, PostingLine.Side.DEBIT);
        var credits = total(lines, PostingLine.Side.CREDIT);
        if (debits.compareTo(credits) != 0) {
            throw new IllegalArgumentException("posting is not balanced: debit=" + debits + ", credit=" + credits);
        }
    }
    public BigDecimal total(PostingLine.Side side) {
        return total(lines, side);
    }
    private static BigDecimal total(List<PostingLine> lines, PostingLine.Side side) {
        return lines.stream().filter(line -> line.side() == side)
                .map(PostingLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
