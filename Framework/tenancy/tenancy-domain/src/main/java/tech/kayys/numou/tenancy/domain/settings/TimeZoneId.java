package tech.kayys.syirkah.tenancy.domain.settings;

import java.util.Objects;

/** Typed timezone identifier (e.g. "UTC", "Asia/Jakarta"). */
public record TimeZoneId(String value) {

    public TimeZoneId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("timezone must not be blank");
        }
    }

    public static TimeZoneId of(String value) {
        return new TimeZoneId(value);
    }

    public static TimeZoneId utc() {
        return new TimeZoneId("UTC");
    }
}
