package tech.kayys.numou.accounting.domain.islamic.syirkah;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Evidence-backed classification supplied by an authorized workflow. This is
 * not itself a determination that a partner owes money; responsibility claims
 * require a separate legal/accounting decision.
 */
public record LossClassification(
        LossCause cause,
        List<String> evidenceIds,
        String classifiedBy,
        Instant classifiedAt,
        String decisionReference
) {
    public LossClassification {
        Objects.requireNonNull(cause, "cause must not be null");
        evidenceIds = List.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds must not be null")).stream()
                .map(id -> SyirkahPartyTerms.requireText(id, "loss classification evidence ID"))
                .distinct().sorted().toList();
        if (cause == LossCause.NOT_APPLICABLE) {
            if (!evidenceIds.isEmpty() || classifiedBy != null || classifiedAt != null || decisionReference != null) {
                throw new IllegalArgumentException("NOT_APPLICABLE classification must not contain loss-determination metadata");
            }
        } else {
            if (evidenceIds.isEmpty()) throw new IllegalArgumentException("Loss classification requires evidence IDs");
            classifiedBy = SyirkahPartyTerms.requireText(classifiedBy, "classifiedBy");
            Objects.requireNonNull(classifiedAt, "classifiedAt must not be null");
            decisionReference = SyirkahPartyTerms.requireText(decisionReference, "decisionReference");
        }
    }

    public static LossClassification notApplicable() {
        return new LossClassification(LossCause.NOT_APPLICABLE, List.of(), null, null, null);
    }

    public static LossClassification classified(
            LossCause cause,
            List<String> evidenceIds,
            String classifiedBy,
            Instant classifiedAt,
            String decisionReference
    ) {
        if (cause == LossCause.NOT_APPLICABLE) throw new IllegalArgumentException("Use notApplicable() for non-loss results");
        return new LossClassification(cause, evidenceIds, classifiedBy, classifiedAt, decisionReference);
    }
}
