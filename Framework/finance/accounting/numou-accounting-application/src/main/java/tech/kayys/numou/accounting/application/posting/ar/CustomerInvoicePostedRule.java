package tech.kayys.syirkah.accounting.application.posting.ar;

import tech.kayys.syirkah.accounting.application.posting.*;
import tech.kayys.syirkah.accounting.domain.ar.*;

import java.math.BigDecimal;
import java.util.ArrayList;

/** Converts AR invoice posting events into a balanced debit/credit instruction. */
public final class CustomerInvoicePostedRule implements PostingRule<CustomerInvoicePosted> {
    @Override public boolean supports(Object event) { return event instanceof CustomerInvoicePosted; }
    @Override public PostingInstruction create(CustomerInvoicePosted event) {
        var lines = new ArrayList<PostingLine>();
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (CustomerInvoiceLine item : event.lines()) {
            if (item.netAmount().signum() > 0) {
                lines.add(new PostingLine(item.revenueAccount(), item.netAmount(), PostingLine.Side.CREDIT));
                net = net.add(item.netAmount());
            }
            tax = tax.add(item.taxAmount());
        }
        if (tax.signum() > 0) {
            if (event.taxAccount() == null || event.taxAccount().isBlank()) {
                throw new IllegalArgumentException("taxAccount is required when tax is present");
            }
            lines.add(new PostingLine(event.taxAccount(), tax, PostingLine.Side.CREDIT));
        }
        var total = net.add(tax);
        if (total.signum() == 0) throw new IllegalArgumentException("cannot post a zero-value customer invoice");
        lines.add(new PostingLine(event.arAccount(), total, PostingLine.Side.DEBIT));
        return new PostingInstruction(lines);
    }
}
