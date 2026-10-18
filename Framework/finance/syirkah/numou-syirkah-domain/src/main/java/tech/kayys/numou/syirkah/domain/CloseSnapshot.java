package tech.kayys.numou.syirkah.domain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable snapshot of one period close: the frozen terms (contract version,
 * nisbah), the financial result, and the exact allocation. The hash chains to the
 * previous close, so later tampering is detectable.
 */
public record CloseSnapshot(CloseId id, int sequence, LocalDate periodStart, LocalDate periodEnd,
                            int contractVersion, String contractCanonical, FinancialResult result,
                            ProfitDisposition disposition, ManagerFault fault, List<AllocationLine> lines,
                            CloseId supersedes, CloseStatus status, long firstMovementSequence,
                            long lastMovementSequence, Instant closedAt, String previousHash, String hash)
        implements Serializable {

    public CloseSnapshot {
        lines = List.copyOf(lines);
    }

    public Money netResult() {
        return result.netResult();
    }

    public static String computeHash(CloseId id, int sequence, LocalDate start, LocalDate end, int contractVersion,
                                     String contractCanonical, FinancialResult result, ProfitDisposition disposition,
                                     List<AllocationLine> lines, CloseId supersedes, String previousHash) {
        StringBuilder sb = new StringBuilder();
        sb.append(id.value()).append('|').append(sequence).append('|').append(start).append('|').append(end)
                .append('|').append(contractVersion).append('|').append(contractCanonical).append('|')
                .append(result.canonical()).append('|').append(disposition).append('|');
        lines.forEach(l -> sb.append(l.canonical()).append(';'));
        sb.append('|').append(supersedes == null ? "-" : supersedes.value()).append('|').append(previousHash);
        return AuditHash.sha256(sb.toString());
    }

    public boolean hashIsValid() {
        return hash.equals(computeHash(id, sequence, periodStart, periodEnd, contractVersion, contractCanonical,
                result, disposition, lines, supersedes, previousHash));
    }

    public CloseSnapshot withStatus(CloseStatus newStatus) {
        return new CloseSnapshot(id, sequence, periodStart, periodEnd, contractVersion, contractCanonical, result,
                disposition, fault, lines, supersedes, newStatus, firstMovementSequence, lastMovementSequence,
                closedAt, previousHash, hash);
    }
}
