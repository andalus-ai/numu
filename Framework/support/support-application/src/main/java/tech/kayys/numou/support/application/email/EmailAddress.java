package tech.kayys.numou.support.application.email;

import java.util.Locale;

public record EmailAddress(String value) {

    public EmailAddress {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("email address cannot be blank");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("email address is invalid");
        }
    }
}
