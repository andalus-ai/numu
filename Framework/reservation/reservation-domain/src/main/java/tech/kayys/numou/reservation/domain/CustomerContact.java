package tech.kayys.numou.reservation.domain;

/**
 * Contact details for the person or organization making the booking.
 */
public record CustomerContact(String name, String phone, String email) {
    public CustomerContact {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Customer name is required");
        }
        if ((phone == null || phone.isBlank()) && (email == null || email.isBlank())) {
            throw new IllegalArgumentException("Customer contact needs at least a phone number or email");
        }
        name = name.trim();
        phone = phone == null ? "" : phone.trim();
        email = email == null ? "" : email.trim().toLowerCase();
    }
}
