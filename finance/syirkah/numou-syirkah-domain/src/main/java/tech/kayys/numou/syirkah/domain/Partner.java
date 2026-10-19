package tech.kayys.numou.syirkah.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * A partner. {@code contributesWork} says whether the partner works in the
 * business (required for abdan, and for the manager in a mudharabah).
 */
public record Partner(PartnerId id, String name, PartnerRole role, boolean contributesWork) implements Serializable {
    public Partner {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("partner name must not be blank");
        }
    }
}
