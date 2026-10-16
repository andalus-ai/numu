package tech.kayys.syirkah.accounting.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

/**
 * Supported payment methods for invoices.
 */
public enum PaymentMethod implements ValueObject {
    CASH,
    BANK_TRANSFER,
    CREDIT_CARD,
    DEBIT_CARD,
    QRIS,
    VIRTUAL_ACCOUNT,
    E_WALLET,
    CHECK
}
