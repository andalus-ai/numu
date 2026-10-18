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

    /**
     * Loss not allocated to partner capital or an evidenced manager-fault liability.
     * This is an entity-level deficit, not a claim against a partner. It is derived
     * from fields already bound by the close audit hash.
     */
    public Money unallocatedLoss() {
        Money zero = Money.zero(netResult().currency());
        if (!netResult().isNegative()) {
            return zero;
        }
        Money allocated = zero;
        for (AllocationLine line : lines) {
            allocated = allocated.add(line.lossBorne()).add(line.managerLiability());
        }
        Money excess = netResult().negate().subtract(allocated);
        return excess.isPositive() ? excess : zero;
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

    /**
     * Versioned hash form that binds the manager-fault finding to the close.
     * The legacy overload remains available so stored pre-upgrade closes can
     * still be verified during migration.
     */
    public static String computeHash(CloseId id, int sequence, LocalDate start, LocalDate end, int contractVersion,
                                     String contractCanonical, FinancialResult result, ProfitDisposition disposition,
                                     ManagerFault fault, List<AllocationLine> lines, CloseId supersedes,
                                     String previousHash) {
        StringBuilder sb = new StringBuilder();
        sb.append("close-v2|").append(id.value()).append('|').append(sequence).append('|')
                .append(start).append('|').append(end).append('|').append(contractVersion).append('|')
                .append(contractCanonical).append('|').append(result.canonical()).append('|')
                .append(disposition).append('|');
        ManagerFault f = fault == null ? ManagerFault.none() : fault;
        sb.append(f.negligentOrBreach()).append('|');
        if (f.negligentOrBreach()) {
            sb.append(f.attributableLoss().currency().code()).append('|')
                    .append(f.attributableLoss().amount().stripTrailingZeros().toPlainString()).append('|')
                    .append(f.reason().length()).append(':').append(f.reason());
        } else {
            sb.append("no-fault");
        }
        sb.append('|');
        lines.forEach(l -> sb.append(l.canonical()).append(';'));
        sb.append('|').append(supersedes == null ? "-" : supersedes.value()).append('|').append(previousHash);
        return AuditHash.sha256(sb.toString());
    }

    /**
     * Current close hash binds the movement range as well as the financial allocation.
     * This prevents persisted metadata from silently pointing at a different ledger slice.
     */
    public static String computeHashV3(CloseId id, int sequence, LocalDate start, LocalDate end, int contractVersion,
                                       String contractCanonical, FinancialResult result, ProfitDisposition disposition,
                                       ManagerFault fault, List<AllocationLine> lines, CloseId supersedes,
                                       String previousHash, long firstMovementSequence, long lastMovementSequence) {
        StringBuilder sb = new StringBuilder();
        sb.append("close-v3|").append(id.value()).append('|').append(sequence).append('|')
                .append(start).append('|').append(end).append('|').append(contractVersion).append('|')
                .append(contractCanonical).append('|').append(result.canonical()).append('|')
                .append(disposition).append('|');
        ManagerFault f = fault == null ? ManagerFault.none() : fault;
        sb.append(f.negligentOrBreach()).append('|');
        if (f.negligentOrBreach()) {
            sb.append(f.attributableLoss().currency().code()).append('|')
                    .append(f.attributableLoss().amount().stripTrailingZeros().toPlainString()).append('|')
                    .append(f.reason().length()).append(':').append(f.reason());
        } else {
            sb.append("no-fault");
        }
        sb.append('|');
        lines.forEach(l -> sb.append(l.canonical()).append(';'));
        sb.append('|').append(supersedes == null ? "-" : supersedes.value())
                .append('|').append(previousHash)
                .append('|').append(firstMovementSequence)
                .append('|').append(lastMovementSequence);
        return AuditHash.sha256(sb.toString());
    }

    public boolean hashIsValid() {
        return hash.equals(computeHashV3(id, sequence, periodStart, periodEnd, contractVersion, contractCanonical,
                result, disposition, fault, lines, supersedes, previousHash, firstMovementSequence, lastMovementSequence))
                || hash.equals(computeHash(id, sequence, periodStart, periodEnd, contractVersion, contractCanonical,
                result, disposition, fault, lines, supersedes, previousHash))
                || hash.equals(computeHash(id, sequence, periodStart, periodEnd, contractVersion, contractCanonical,
                result, disposition, lines, supersedes, previousHash));
    }

    public CloseSnapshot withStatus(CloseStatus newStatus) {
        return new CloseSnapshot(id, sequence, periodStart, periodEnd, contractVersion, contractCanonical, result,
                disposition, fault, lines, supersedes, newStatus, firstMovementSequence, lastMovementSequence,
                closedAt, previousHash, hash);
    }
}
