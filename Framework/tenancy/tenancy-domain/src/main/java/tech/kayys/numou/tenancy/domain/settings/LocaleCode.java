package tech.kayys.syirkah.tenancy.domain.settings;

/** Typed locale code (e.g. "en", "id", "ar"). */
public record LocaleCode(String value) {

    public LocaleCode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("locale must not be blank");
        }
    }

    public static LocaleCode of(String value) {
        return new LocaleCode(value);
    }

    public static LocaleCode english() {
        return new LocaleCode("en");
    }
}
