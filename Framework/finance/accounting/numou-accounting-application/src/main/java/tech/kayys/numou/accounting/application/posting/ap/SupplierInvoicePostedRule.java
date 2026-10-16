package tech.kayys.syirkah.accounting.application.posting.ap;

import tech.kayys.syirkah.accounting.application.posting.*;
import tech.kayys.syirkah.accounting.domain.ap.*;

import java.math.BigDecimal;
import java.util.ArrayList;

/** Converts AP invoice posting events into a balanced debit/credit instruction. */
public final class SupplierInvoicePostedRule implements PostingRule<SupplierInvoicePosted> {
    @Override public boolean supports(Object event) { return event instanceof SupplierInvoicePosted; }
    @Override public PostingInstruction create(SupplierInvoicePosted event) {
        var lines = new ArrayList<PostingLine>();
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (VendorInvoiceLine item : event.lines()) {
            if (item.netAmount().signum() > 0) {
                lines.add(new PostingLine(item.accountCode(), item.netAmount(), PostingLine.Side.DEBIT));
                net = net.add(item.netAmount());
            }
            tax = tax.add(item.taxAmount());
        }
        if (tax.signum() > 0) {
            if (event.taxAccount() == null || event.taxAccount().isBlank()) {
                throw new IllegalArgumentException("taxAccount is required when tax is present");
            }
            lines.add(new PostingLine(event.taxAccount(), tax, PostingLine.Side.DEBIT));
        }
        var total = net.add(tax);
        if (total.signum() == 0) throw new IllegalArgumentException("cannot post a zero-value supplier invoice");
        lines.add(new PostingLine(event.apAccount(), total, PostingLine.Side.CREDIT));
        return new PostingInstruction(lines);
    }
}
