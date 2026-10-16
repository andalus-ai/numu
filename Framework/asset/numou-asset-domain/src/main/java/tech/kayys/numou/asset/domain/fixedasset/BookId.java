package tech.kayys.syirkah.asset.domain.fixedasset;

import java.util.Objects;

/**
 * Identity of a depreciation book (e.g. CORP, TAX, IFRS, AAOIFI).
 */
public record BookId(String value) {
    public BookId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("BookId must not be blank");
    }

    public static final BookId CORP = new BookId("CORP");
    public static final BookId TAX = new BookId("TAX");
    public static final BookId IFRS = new BookId("IFRS");
}
