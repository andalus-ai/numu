package tech.kayys.syirkah.accounting.application.api.query;

import tech.kayys.syirkah.accounting.domain.identifier.InvoiceId;
import tech.kayys.syirkah.foundation.application.query.Query;

public record GetInvoiceQuery(InvoiceId invoiceId) implements Query {}
