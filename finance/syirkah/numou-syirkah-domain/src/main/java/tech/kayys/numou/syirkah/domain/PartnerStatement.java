package tech.kayys.numou.syirkah.domain;

import java.io.Serializable;
import java.util.List;

/** Chronological statement of one partner: every movement with running balances, and the closing position. */
public record PartnerStatement(PartnerId partner, String name, List<StatementLine> lines, PartnerPosition closing)
        implements Serializable {
    public PartnerStatement {
        lines = List.copyOf(lines);
    }
}
