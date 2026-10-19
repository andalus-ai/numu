package tech.kayys.numou.accounting.application.api.query;

import tech.kayys.numou.accounting.domain.identifier.InvoiceId;
import tech.kayys.numou.foundation.application.query.Query;

public record GetInvoiceQuery(InvoiceId invoiceId) implements Query {}
