
package tech.kayys.syirkah.purchasing.domain.procurement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class GoodsReceipt {

    private final String receiptId;
    private final String tenantId;
    private final String ledgerId;
    private final String poId;
    private final String poLineId;
    private final BigDecimal quantityReceived;
    private final Instant receivedAt;
    private final String receivedBy;

    public GoodsReceipt(
            String receiptId,
            String tenantId,
            String ledgerId,
            String poId,
            String poLineId,
            BigDecimal quantityReceived,
            String receivedBy) {
        this.receiptId = Objects.requireNonNull(receiptId, "receiptId cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.ledgerId = Objects.requireNonNull(ledgerId, "ledgerId cannot be null");
        this.poId = Objects.requireNonNull(poId, "poId cannot be null");
        this.poLineId = Objects.requireNonNull(poLineId, "poLineId cannot be null");
        this.quantityReceived = Objects.requireNonNull(quantityReceived, "quantityReceived cannot be null");
        if (quantityReceived.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantityReceived must be positive");
        }
        this.receivedBy = Objects.requireNonNull(receivedBy, "receivedBy cannot be null");
        this.receivedAt = Instant.now();
    }

    public String receiptId() { return receiptId; }
    public String tenantId() { return tenantId; }
    public String ledgerId() { return ledgerId; }
    public String poId() { return poId; }
    public String poLineId() { return poLineId; }
    public BigDecimal quantityReceived() { return quantityReceived; }
    public Instant receivedAt() { return receivedAt; }
    public String receivedBy() { return receivedBy; }
}
