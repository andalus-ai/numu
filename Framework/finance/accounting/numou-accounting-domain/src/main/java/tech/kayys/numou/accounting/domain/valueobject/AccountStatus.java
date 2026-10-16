package tech.kayys.syirkah.accounting.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

public enum AccountStatus implements ValueObject {
    ACTIVE,
    INACTIVE,
    FROZEN,
    CLOSED
}
