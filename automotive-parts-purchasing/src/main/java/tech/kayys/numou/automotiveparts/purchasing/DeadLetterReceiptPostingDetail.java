package tech.kayys.numou.automotiveparts.purchasing;

import java.util.List;

/** Dead-letter receipt details and immutable operator recovery history. */
public record DeadLetterReceiptPostingDetail(
        DeadLetterReceiptPosting posting,
        List<ReceiptPostingRequeueAudit> requeueHistory) {
    public DeadLetterReceiptPostingDetail {
        requeueHistory = requeueHistory == null ? List.of() : List.copyOf(requeueHistory);
    }
}
