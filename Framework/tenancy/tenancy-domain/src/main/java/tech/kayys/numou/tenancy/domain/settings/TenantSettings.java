package tech.kayys.syirkah.tenancy.domain.settings;

import tech.kayys.syirkah.foundation.domain.audit.AuditMeta;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/**
 * Platform-level settings for a Tenant (exactly one per Tenant).
 *
 * <p>Only holds platform-portable configuration (timezone, locale, date/time format).
 * Application-specific configuration (POS, Payroll, Warehouse) belongs in those
 * respective modules, NOT here.
 */
public final class TenantSettings {

    private final TenantId tenantId;
    private TimeZoneId timezone;
    private LocaleCode locale;
    private String dateFormat;
    private String timeFormat;
    private final AuditMeta audit;

    private TenantSettings(TenantId tenantId, TimeZoneId timezone, LocaleCode locale,
                            String dateFormat, String timeFormat, AuditMeta audit) {
        this.tenantId   = Objects.requireNonNull(tenantId);
        this.timezone   = Objects.requireNonNull(timezone);
        this.locale     = Objects.requireNonNull(locale);
        this.dateFormat = requireText(dateFormat, "dateFormat");
        this.timeFormat = requireText(timeFormat, "timeFormat");
        this.audit      = Objects.requireNonNull(audit);
    }

    /** Creates default platform settings (UTC / en / ISO formats). */
    public static TenantSettings defaults(TenantId tenantId, AuditMeta audit) {
        return new TenantSettings(
                tenantId,
                TimeZoneId.utc(),
                LocaleCode.english(),
                "yyyy-MM-dd",
                "HH:mm",
                audit
        );
    }

    /** Creates fully specified settings. */
    public static TenantSettings create(TenantId tenantId, TimeZoneId timezone,
                                         LocaleCode locale, String dateFormat,
                                         String timeFormat, AuditMeta audit) {
        return new TenantSettings(tenantId, timezone, locale, dateFormat, timeFormat, audit);
    }

    public void changeTimezone(TimeZoneId timezone) {
        this.timezone = Objects.requireNonNull(timezone);
    }

    public void changeLocale(LocaleCode locale) {
        this.locale = Objects.requireNonNull(locale);
    }

    public void changeDateFormat(String dateFormat) {
        this.dateFormat = requireText(dateFormat, "dateFormat");
    }

    public void changeTimeFormat(String timeFormat) {
        this.timeFormat = requireText(timeFormat, "timeFormat");
    }

    // ── accessors ────────────────────────────────────────────────────────────

    public TenantId tenantId()    { return tenantId; }
    public TimeZoneId timezone()  { return timezone; }
    public LocaleCode locale()    { return locale; }
    public String dateFormat()    { return dateFormat; }
    public String timeFormat()    { return timeFormat; }
    public AuditMeta audit()      { return audit; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
