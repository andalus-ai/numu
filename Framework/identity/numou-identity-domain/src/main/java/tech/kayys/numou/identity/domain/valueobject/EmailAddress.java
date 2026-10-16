package tech.kayys.syirkah.identity.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A deliberately simple validator - it rejects the obviously wrong
 * shape, not every RFC 5322 edge case. Real deliverability is
 * confirmed by actually sending a verification email, not by regex.
 */
public record EmailAddress(String value) implements ValueObject {

    private static final Pattern SIMPLE_EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public EmailAddress {
        Objects.requireNonNull(value, "Email cannot be null");

        value = value.trim().toLowerCase(Locale.ROOT);

        if (!SIMPLE_EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid email address: " + value);
        }
    }

    public static EmailAddress of(String value) {
        return new EmailAddress(value);
    }

}
