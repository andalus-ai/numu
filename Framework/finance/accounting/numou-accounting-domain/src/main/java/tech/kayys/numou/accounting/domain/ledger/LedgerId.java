package tech.kayys.syirkah.accounting.domain.ledger;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;
import java.util.Objects;
import java.util.UUID;

/**
 * Identifier for financial ledgers (Primary, Tax, IFRS, Shariah, Management).
 */
public record LedgerId(String value) implements DomainId<String>, ValueObject {
    public LedgerId {
        Objects.requireNonNull(value, "LedgerId value cannot be null");
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("LedgerId cannot be blank");
        }
    }

    public String getValue() { return value; }

    public static LedgerId of(String value) {
        return new LedgerId(value);
    }

    public static LedgerId generate() {
        return new LedgerId(UUID.randomUUID().toString());
    }

    public static LedgerId primary() {
        return new LedgerId("primary");
    }
}
