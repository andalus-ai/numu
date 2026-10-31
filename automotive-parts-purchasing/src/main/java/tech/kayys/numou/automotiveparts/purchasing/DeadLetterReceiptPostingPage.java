package tech.kayys.numou.automotiveparts.purchasing;

import java.util.List;

/** Keyset-paginated dead-letter result; nextCursor is null when the result is exhausted. */
public record DeadLetterReceiptPostingPage(
        List<DeadLetterReceiptPosting> items,
        String nextCursor) {
    public DeadLetterReceiptPostingPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
