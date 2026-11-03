package tech.kayys.numou.accounting.domain.ap;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Root aggregate for a vendor invoice in Accounts Payable. */
public final class VendorInvoice {
    private final VendorInvoiceId id;
    private final VendorInvoiceScope scope;
    private final VendorId vendorId;
    private final String vendorRef;
    private final String currency;
    private final List<VendorInvoiceLine> lines;
    private VendorInvoiceStatus status;
    private final List<Object> domainEvents = new ArrayList<>();

    public VendorInvoice(VendorInvoiceId id, VendorInvoiceScope scope, VendorId vendorId,
                         String vendorRef, String currency, List<VendorInvoiceLine> lines) {
        this.id = Objects.requireNonNull(id, "id");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.vendorId = Objects.requireNonNull(vendorId, "vendorId");
        this.vendorRef = requireText(vendorRef, "vendorRef");
        this.currency = requireText(currency, "currency");
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("VendorInvoice must have at least one line");
        this.lines = List.copyOf(lines);
        this.status = VendorInvoiceStatus.DRAFT;
        domainEvents.add(new VendorInvoiceRegistered(id, vendorId, vendorRef, currency, Instant.now()));
    }

    public void post(String apAccount, String taxAccount) {
        if (status != VendorInvoiceStatus.DRAFT) throw new IllegalStateException("Only DRAFT invoices can be posted; current=" + status);
        requireText(apAccount, "apAccount");
        status = VendorInvoiceStatus.POSTED;
        domainEvents.add(new SupplierInvoicePosted(id, vendorId, vendorRef, apAccount, taxAccount, lines, currency, Instant.now()));
    }

    public void cancel() {
        if (status != VendorInvoiceStatus.DRAFT) throw new IllegalStateException("Only DRAFT invoices can be cancelled; current=" + status);
        status = VendorInvoiceStatus.CANCELLED;
    }

    public BigDecimal totalAmount() { return lines.stream().map(VendorInvoiceLine::total).reduce(BigDecimal.ZERO, BigDecimal::add); }
    public BigDecimal netAmount() { return lines.stream().map(VendorInvoiceLine::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add); }
    public BigDecimal taxAmount() { return lines.stream().map(VendorInvoiceLine::taxAmount).reduce(BigDecimal.ZERO, BigDecimal::add); }
    public VendorInvoiceId id() { return id; }
    public VendorInvoiceScope scope() { return scope; }
    public VendorId vendorId() { return vendorId; }
    public String vendorRef() { return vendorRef; }
    public String currency() { return currency; }
    public List<VendorInvoiceLine> lines() { return lines; }
    public VendorInvoiceStatus status() { return status; }
    public List<Object> domainEvents() { return List.copyOf(domainEvents); }
    public void clearEvents() { domainEvents.clear(); }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
