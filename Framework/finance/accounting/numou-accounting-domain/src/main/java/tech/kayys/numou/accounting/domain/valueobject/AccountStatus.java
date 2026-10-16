package tech.kayys.numou.accounting.domain.valueobject;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

public enum AccountStatus implements ValueObject {
    ACTIVE,
    INACTIVE,
    FROZEN,
    CLOSED
}
