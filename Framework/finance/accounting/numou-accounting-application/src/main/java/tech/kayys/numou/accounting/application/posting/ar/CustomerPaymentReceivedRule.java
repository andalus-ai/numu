package tech.kayys.syirkah.accounting.application.posting.ar;

import tech.kayys.syirkah.accounting.application.posting.*;
import tech.kayys.syirkah.accounting.domain.ar.CustomerPaymentReceived;

import java.util.List;

/** Converts customer receipts to DR cash / CR receivables. */
public final class CustomerPaymentReceivedRule implements PostingRule<CustomerPaymentReceived> {
    @Override public boolean supports(Object event) { return event instanceof CustomerPaymentReceived; }
    @Override public PostingInstruction create(CustomerPaymentReceived event) {
        return new PostingInstruction(List.of(
                new PostingLine(event.cashAccount(), event.amount(), PostingLine.Side.DEBIT),
                new PostingLine(event.arAccount(), event.amount(), PostingLine.Side.CREDIT)));
    }
}
