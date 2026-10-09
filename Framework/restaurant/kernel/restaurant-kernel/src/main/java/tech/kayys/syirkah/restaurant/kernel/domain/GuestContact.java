package tech.kayys.syirkah.restaurant.kernel.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * How to reach a guest. Personal data: {@link #toString()} is masked so an accidental
 * {@code log.info("{}", reservation)} cannot leak a guest's phone number or email.
 */
public record GuestContact(String name, String phone, String email) {

    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9 ()-]{6,18}[0-9]$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public GuestContact {
        Objects.requireNonNull(name, "guest name cannot be null");
        name = name.trim();
        if (name.isEmpty() || name.length() > 120) {
            throw new IllegalArgumentException("Guest name must be 1-120 characters");
        }
        phone = blankToNull(phone);
        email = blankToNull(email);
        if (phone == null && email == null) {
            throw new IllegalArgumentException("A phone number or an email is required to reach the guest");
        }
        if (phone != null && !PHONE.matcher(phone).matches()) {
            throw new IllegalArgumentException("Invalid phone number");
        }
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email address");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @Override
    public String toString() {
        return "GuestContact[name=" + mask(name) + ", phone=" + (phone == null ? "-" : "***" + phone.substring(Math.max(0, phone.length() - 2)))
                + ", email=" + (email == null ? "-" : email.charAt(0) + "***@" + email.substring(email.indexOf('@') + 1)) + "]";
    }

    private static String mask(String n) {
        return n.charAt(0) + "*".repeat(Math.max(0, n.length() - 1));
    }
}
