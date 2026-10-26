package tech.kayys.numou.hospitality.domain;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Hospitality-owned property metadata and publication lifecycle.
 * Tenant and provider identifiers are authorization references; authorization itself
 * must be enforced by the application boundary on every command.
 */
public final class PropertyListing {
    private final UUID id;
    private final String tenantId;
    private final String providerId;
    private final PropertyType propertyType;
    private String title;
    private String address;
    private String timeZone;
    private LocalTime checkInFrom;
    private LocalTime checkInUntil;
    private LocalTime checkOutTime;
    private ListingStatus status;

    private PropertyListing(UUID id, String tenantId, String providerId, PropertyType propertyType, String title) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = requireText(tenantId, "tenantId");
        this.providerId = requireText(providerId, "providerId");
        this.propertyType = Objects.requireNonNull(propertyType, "propertyType");
        this.title = requireText(title, "title");
        this.status = ListingStatus.DRAFT;
    }

    public static PropertyListing draft(UUID id, String tenantId, String providerId, PropertyType propertyType, String title) {
        return new PropertyListing(id, tenantId, providerId, propertyType, title);
    }

    /** Updates operational details. Published listings must be suspended before editing. */
    public void updateDetails(String title, String address, String timeZone, LocalTime checkInFrom,
                              LocalTime checkInUntil, LocalTime checkOutTime) {
        if (status != ListingStatus.DRAFT && status != ListingStatus.SUSPENDED) {
            throw new IllegalStateException("Only draft or suspended listings can be edited");
        }
        String validatedTitle = requireText(title, "title");
        String validatedAddress = requireText(address, "address");
        String validatedTimeZone = requireText(timeZone, "timeZone");
        ZoneId.of(validatedTimeZone);
        Objects.requireNonNull(checkInFrom, "checkInFrom");
        Objects.requireNonNull(checkInUntil, "checkInUntil");
        Objects.requireNonNull(checkOutTime, "checkOutTime");
        if (checkInUntil.isBefore(checkInFrom)) {
            throw new IllegalArgumentException("checkInUntil must not be before checkInFrom");
        }
        this.title = validatedTitle;
        this.address = validatedAddress;
        this.timeZone = validatedTimeZone;
        this.checkInFrom = checkInFrom;
        this.checkInUntil = checkInUntil;
        this.checkOutTime = checkOutTime;
    }

    public void publish() {
        if (status != ListingStatus.DRAFT && status != ListingStatus.SUSPENDED) {
            throw new IllegalStateException("Only draft or suspended listings can be published");
        }
        if (address == null || timeZone == null || checkInFrom == null || checkInUntil == null || checkOutTime == null) {
            throw new IllegalStateException("Listing details must be complete before publication");
        }
        status = ListingStatus.PUBLISHED;
    }

    public void suspend() {
        if (status != ListingStatus.PUBLISHED) {
            throw new IllegalStateException("Only published listings can be suspended");
        }
        status = ListingStatus.SUSPENDED;
    }

    public void archive() {
        if (status == ListingStatus.ARCHIVED) return;
        status = ListingStatus.ARCHIVED;
    }

    public boolean isBookable() { return status == ListingStatus.PUBLISHED; }
    public UUID id() { return id; }
    public String tenantId() { return tenantId; }
    public String providerId() { return providerId; }
    public PropertyType propertyType() { return propertyType; }
    public String title() { return title; }
    public String address() { return address; }
    public String timeZone() { return timeZone; }
    public LocalTime checkInFrom() { return checkInFrom; }
    public LocalTime checkInUntil() { return checkInUntil; }
    public LocalTime checkOutTime() { return checkOutTime; }
    public ListingStatus status() { return status; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
