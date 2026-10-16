package tech.kayys.syirkah.company.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;
import tech.kayys.syirkah.company.domain.identifier.CompanyId;
import tech.kayys.syirkah.company.domain.valueobject.Address;
import tech.kayys.syirkah.company.domain.valueobject.CompanyStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Company aggregate root.
 * Represents the core organization entity in the ERP system.
 */
public final class Company extends AbstractAggregateRoot<CompanyId> {
    
    private static final long serialVersionUID = 1L;
    
    private String name;
    private String legalName;
    private String registrationNumber;
    private String taxId;
    private String vatNumber;
    private String email;
    private String phone;
    private Address address;
    private String website;
    private String industry;
    private String businessType;
    private int employeeCount;
    private CompanyStatus status;
    private String currencyCode;
    private String timezone;
    private String language;
    private String logoUrl;
    private String faviconUrl;
    private List<String> domains;
    private List<String> socialMediaLinks;
    private String createdBy;
    private String updatedBy;
    private CompanySettings settings;
    private boolean active;

    private Company(CompanyId id) {
        super(id);
        this.status = CompanyStatus.ACTIVE;
        this.active = true;
        this.domains = new ArrayList<>();
        this.socialMediaLinks = new ArrayList<>();
        this.settings = CompanySettings.defaultSettings();
    }

    private Company() {
        super();
    }

    /**
     * Factory method to create a new company.
     */
    public static Company create(
            CompanyId id,
            String name,
            String legalName,
            String email,
            String currencyCode,
            String timezone) {
        Company company = new Company(id);
        company.name = name;
        company.legalName = legalName;
        company.email = email;
        company.currencyCode = currencyCode;
        company.timezone = timezone;
        company.status = CompanyStatus.ACTIVE;
        return company;
    }

    /**
     * Updates company basic information.
     */
    public void updateBasicInfo(String name, String legalName, String email, String phone) {
        if (status == CompanyStatus.CLOSED) {
            throw new IllegalStateException("Cannot update closed company");
        }
        this.name = name;
        this.legalName = legalName;
        this.email = email;
        this.phone = phone;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Updates company address.
     */
    public void updateAddress(Address address) {
        if (status == CompanyStatus.CLOSED) {
            throw new IllegalStateException("Cannot update closed company");
        }
        this.address = address;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Updates company settings.
     */
    public void updateSettings(CompanySettings settings) {
        if (status == CompanyStatus.CLOSED) {
            throw new IllegalStateException("Cannot update closed company");
        }
        this.settings = settings;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds a domain to the company.
     */
    public void addDomain(String domain) {
        if (!domains.contains(domain)) {
            domains.add(domain);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    /**
     * Removes a domain from the company.
     */
    public void removeDomain(String domain) {
        domains.remove(domain);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Deactivates the company.
     */
    public void deactivate() {
        if (status == CompanyStatus.CLOSED) {
            throw new IllegalStateException("Company is already closed");
        }
        this.status = CompanyStatus.INACTIVE;
        this.active = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Activates the company.
     */
    public void activate() {
        if (status == CompanyStatus.CLOSED) {
            throw new IllegalStateException("Cannot activate closed company");
        }
        this.status = CompanyStatus.ACTIVE;
        this.active = true;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Suspends the company.
     */
    public void suspend(String reason) {
        if (status == CompanyStatus.CLOSED) {
            throw new IllegalStateException("Cannot suspend closed company");
        }
        this.status = CompanyStatus.SUSPENDED;
        this.active = false;
        this.settings = settings.withSuspensionReason(reason);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Closes the company.
     */
    public void close(String reason) {
        this.status = CompanyStatus.CLOSED;
        this.active = false;
        this.settings = settings.withClosureReason(reason).withClosureDate(Instant.now());
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    // Getters and Setters
    public String getName() { return name; }
    public String getLegalName() { return legalName; }
    public String getRegistrationNumber() { return registrationNumber; }
    public String getTaxId() { return taxId; }
    public String getVatNumber() { return vatNumber; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public Address getAddress() { return address; }
    public String getWebsite() { return website; }
    public String getIndustry() { return industry; }
    public String getBusinessType() { return businessType; }
    public int getEmployeeCount() { return employeeCount; }
    public CompanyStatus getStatus() { return status; }
    public String getCurrencyCode() { return currencyCode; }
    public String getTimezone() { return timezone; }
    public String getLanguage() { return language; }
    public String getLogoUrl() { return logoUrl; }
    public String getFaviconUrl() { return faviconUrl; }
    public List<String> getDomains() { return Collections.unmodifiableList(domains); }
    public List<String> getSocialMediaLinks() { return Collections.unmodifiableList(socialMediaLinks); }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public CompanySettings getSettings() { return settings; }
    public boolean isActive() { return active && status == CompanyStatus.ACTIVE; }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setVatNumber(String vatNumber) {
        this.vatNumber = vatNumber;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setWebsite(String website) {
        this.website = website;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setIndustry(String industry) {
        this.industry = industry;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setEmployeeCount(int employeeCount) {
        this.employeeCount = employeeCount;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setLanguage(String language) {
        this.language = language;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setFaviconUrl(String faviconUrl) {
        this.faviconUrl = faviconUrl;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setSocialMediaLinks(List<String> socialMediaLinks) {
        this.socialMediaLinks = new ArrayList<>(socialMediaLinks);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "Company{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", legalName='" + legalName + '\'' +
                ", status=" + status +
                ", currencyCode='" + currencyCode + '\'' +
                '}';
    }

    /**
     * Company settings value object.
     */
    public static final class CompanySettings implements ValueObject {
        private static final long serialVersionUID = 1L;
        
        private final boolean multiCurrency;
        private final boolean multiLanguage;
        private final boolean multiBrand;
        private final boolean allowGuestCheckout;
        private final boolean enableCustomerRegistration;
        private final String defaultTheme;
        private final String defaultCurrency;
        private final String defaultLanguage;
        private final String suspensionReason;
        private final String closureReason;
        private final Instant closureDate;
        private final int maxUsers;
        private final int maxStorageGb;
        private final boolean enableAuditLog;
        private final int sessionTimeoutMinutes;
        private final int passwordExpiryDays;
        private final boolean twoFactorAuth;

        public CompanySettings(
                boolean multiCurrency,
                boolean multiLanguage,
                boolean multiBrand,
                boolean allowGuestCheckout,
                boolean enableCustomerRegistration,
                String defaultTheme,
                String defaultCurrency,
                String defaultLanguage,
                String suspensionReason,
                String closureReason,
                Instant closureDate,
                int maxUsers,
                int maxStorageGb,
                boolean enableAuditLog,
                int sessionTimeoutMinutes,
                int passwordExpiryDays,
                boolean twoFactorAuth) {
            this.multiCurrency = multiCurrency;
            this.multiLanguage = multiLanguage;
            this.multiBrand = multiBrand;
            this.allowGuestCheckout = allowGuestCheckout;
            this.enableCustomerRegistration = enableCustomerRegistration;
            this.defaultTheme = defaultTheme;
            this.defaultCurrency = defaultCurrency;
            this.defaultLanguage = defaultLanguage;
            this.suspensionReason = suspensionReason;
            this.closureReason = closureReason;
            this.closureDate = closureDate;
            this.maxUsers = maxUsers;
            this.maxStorageGb = maxStorageGb;
            this.enableAuditLog = enableAuditLog;
            this.sessionTimeoutMinutes = sessionTimeoutMinutes;
            this.passwordExpiryDays = passwordExpiryDays;
            this.twoFactorAuth = twoFactorAuth;
            validate();
        }

        @Override
        public void validate() {
            if (maxUsers <= 0) {
                throw new IllegalArgumentException("Max users must be positive");
            }
            if (maxStorageGb < 0) {
                throw new IllegalArgumentException("Max storage cannot be negative");
            }
            if (sessionTimeoutMinutes <= 0) {
                throw new IllegalArgumentException("Session timeout must be positive");
            }
        }

        // Getters
        public boolean isMultiCurrency() { return multiCurrency; }
        public boolean isMultiLanguage() { return multiLanguage; }
        public boolean isMultiBrand() { return multiBrand; }
        public boolean isAllowGuestCheckout() { return allowGuestCheckout; }
        public boolean isEnableCustomerRegistration() { return enableCustomerRegistration; }
        public String getDefaultTheme() { return defaultTheme; }
        public String getDefaultCurrency() { return defaultCurrency; }
        public String getDefaultLanguage() { return defaultLanguage; }
        public String getSuspensionReason() { return suspensionReason; }
        public String getClosureReason() { return closureReason; }
        public Instant getClosureDate() { return closureDate; }
        public int getMaxUsers() { return maxUsers; }
        public int getMaxStorageGb() { return maxStorageGb; }
        public boolean isEnableAuditLog() { return enableAuditLog; }
        public int getSessionTimeoutMinutes() { return sessionTimeoutMinutes; }
        public int getPasswordExpiryDays() { return passwordExpiryDays; }
        public boolean isTwoFactorAuth() { return twoFactorAuth; }

        public CompanySettings withMultiCurrency(boolean multiCurrency) {
            return new CompanySettings(
                multiCurrency, multiLanguage, multiBrand, allowGuestCheckout,
                enableCustomerRegistration, defaultTheme, defaultCurrency,
                defaultLanguage, suspensionReason, closureReason, closureDate,
                maxUsers, maxStorageGb, enableAuditLog, sessionTimeoutMinutes,
                passwordExpiryDays, twoFactorAuth
            );
        }

        public CompanySettings withSuspensionReason(String reason) {
            return new CompanySettings(
                multiCurrency, multiLanguage, multiBrand, allowGuestCheckout,
                enableCustomerRegistration, defaultTheme, defaultCurrency,
                defaultLanguage, reason, closureReason, closureDate,
                maxUsers, maxStorageGb, enableAuditLog, sessionTimeoutMinutes,
                passwordExpiryDays, twoFactorAuth
            );
        }

        public CompanySettings withClosureReason(String reason) {
            return new CompanySettings(
                multiCurrency, multiLanguage, multiBrand, allowGuestCheckout,
                enableCustomerRegistration, defaultTheme, defaultCurrency,
                defaultLanguage, suspensionReason, reason, closureDate,
                maxUsers, maxStorageGb, enableAuditLog, sessionTimeoutMinutes,
                passwordExpiryDays, twoFactorAuth
            );
        }

        public CompanySettings withClosureDate(Instant date) {
            return new CompanySettings(
                multiCurrency, multiLanguage, multiBrand, allowGuestCheckout,
                enableCustomerRegistration, defaultTheme, defaultCurrency,
                defaultLanguage, suspensionReason, closureReason, date,
                maxUsers, maxStorageGb, enableAuditLog, sessionTimeoutMinutes,
                passwordExpiryDays, twoFactorAuth
            );
        }

        public static CompanySettings defaultSettings() {
            return new CompanySettings(
                true,  // multiCurrency
                true,  // multiLanguage
                false, // multiBrand
                false, // allowGuestCheckout
                true,  // enableCustomerRegistration
                "default", // defaultTheme
                "USD", // defaultCurrency
                "en",  // defaultLanguage
                null,  // suspensionReason
                null,  // closureReason
                null,  // closureDate
                100,   // maxUsers
                10,    // maxStorageGb
                true,  // enableAuditLog
                60,    // sessionTimeoutMinutes
                90,    // passwordExpiryDays
                false  // twoFactorAuth
            );
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private boolean multiCurrency = true;
            private boolean multiLanguage = true;
            private boolean multiBrand = false;
            private boolean allowGuestCheckout = false;
            private boolean enableCustomerRegistration = true;
            private String defaultTheme = "default";
            private String defaultCurrency = "USD";
            private String defaultLanguage = "en";
            private String suspensionReason;
            private String closureReason;
            private Instant closureDate;
            private int maxUsers = 100;
            private int maxStorageGb = 10;
            private boolean enableAuditLog = true;
            private int sessionTimeoutMinutes = 60;
            private int passwordExpiryDays = 90;
            private boolean twoFactorAuth = false;

            public Builder multiCurrency(boolean multiCurrency) {
                this.multiCurrency = multiCurrency;
                return this;
            }

            public Builder multiLanguage(boolean multiLanguage) {
                this.multiLanguage = multiLanguage;
                return this;
            }

            public Builder multiBrand(boolean multiBrand) {
                this.multiBrand = multiBrand;
                return this;
            }

            public Builder allowGuestCheckout(boolean allowGuestCheckout) {
                this.allowGuestCheckout = allowGuestCheckout;
                return this;
            }

            public Builder enableCustomerRegistration(boolean enableCustomerRegistration) {
                this.enableCustomerRegistration = enableCustomerRegistration;
                return this;
            }

            public Builder defaultTheme(String defaultTheme) {
                this.defaultTheme = defaultTheme;
                return this;
            }

            public Builder defaultCurrency(String defaultCurrency) {
                this.defaultCurrency = defaultCurrency;
                return this;
            }

            public Builder defaultLanguage(String defaultLanguage) {
                this.defaultLanguage = defaultLanguage;
                return this;
            }

            public Builder maxUsers(int maxUsers) {
                this.maxUsers = maxUsers;
                return this;
            }

            public Builder maxStorageGb(int maxStorageGb) {
                this.maxStorageGb = maxStorageGb;
                return this;
            }

            public Builder enableAuditLog(boolean enableAuditLog) {
                this.enableAuditLog = enableAuditLog;
                return this;
            }

            public Builder sessionTimeoutMinutes(int sessionTimeoutMinutes) {
                this.sessionTimeoutMinutes = sessionTimeoutMinutes;
                return this;
            }

            public Builder passwordExpiryDays(int passwordExpiryDays) {
                this.passwordExpiryDays = passwordExpiryDays;
                return this;
            }

            public Builder twoFactorAuth(boolean twoFactorAuth) {
                this.twoFactorAuth = twoFactorAuth;
                return this;
            }

            public CompanySettings build() {
                return new CompanySettings(
                    multiCurrency, multiLanguage, multiBrand, allowGuestCheckout,
                    enableCustomerRegistration, defaultTheme, defaultCurrency,
                    defaultLanguage, suspensionReason, closureReason, closureDate,
                    maxUsers, maxStorageGb, enableAuditLog, sessionTimeoutMinutes,
                    passwordExpiryDays, twoFactorAuth
                );
            }
        }
    }
}
