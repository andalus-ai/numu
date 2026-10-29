package tech.kayys.numou.crm.infrastructure.persistence.entity;

import tech.kayys.numou.foundation.persistence.BaseEntity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Customer entity for persistence.
 */
@Entity
@Table(name = "crm_customers", indexes = {
    @Index(name = "idx_customer_email", columnList = "email"),
    @Index(name = "idx_customer_number", columnList = "customer_number"),
    @Index(name = "idx_customer_company", columnList = "company_name")
})
public class CustomerEntity extends BaseEntity {

    /** Null for unreconciled legacy rows; tenant-scoped operations never expose those rows. */
    @Column(name = "tenant_id")
    public UUID tenantId;

    @Column(name = "customer_number", length = 50)
    public String customerNumber;

    @Column(name = "company_name", length = 100)
    public String companyName;

    @Column(name = "first_name", length = 50)
    public String firstName;

    @Column(name = "last_name", length = 50)
    public String lastName;

    @Column(name = "email", length = 100)
    public String email;

    @Column(name = "phone", length = 20)
    public String phone;

    @Column(name = "address", length = 255)
    public String address;

    @Column(name = "city", length = 50)
    public String city;

    @Column(name = "state", length = 50)
    public String state;

    @Column(name = "postal_code", length = 20)
    public String postalCode;

    @Column(name = "country", length = 50)
    public String country;

    @Column(name = "industry", length = 50)
    public String industry;

    @Column(name = "website", length = 100)
    public String website;

    @Column(name = "tax_id", length = 50)
    public String taxId;

    @Column(name = "currency_code", length = 3)
    public String currencyCode;

    @Column(name = "payment_terms", length = 50)
    public String paymentTerms;

    @Column(name = "credit_limit", length = 50)
    public String creditLimit;

    @Column(name = "account_status", length = 20)
    public String accountStatus;

    @Column(name = "notes", length = 2000)
    public String notes;

    @ElementCollection
    @CollectionTable(name = "crm_customer_contacts", joinColumns = @JoinColumn(name = "customer_id"))
    @AttributeOverrides({
        @AttributeOverride(name = "id", column = @Column(name = "contact_id")),
        @AttributeOverride(name = "firstName", column = @Column(name = "first_name", length = 50)),
        @AttributeOverride(name = "lastName", column = @Column(name = "last_name", length = 50)),
        @AttributeOverride(name = "email", column = @Column(name = "email", length = 100)),
        @AttributeOverride(name = "phone", column = @Column(name = "phone", length = 20)),
        @AttributeOverride(name = "jobTitle", column = @Column(name = "job_title", length = 100)),
        @AttributeOverride(name = "department", column = @Column(name = "department", length = 50)),
        @AttributeOverride(name = "primary", column = @Column(name = "is_primary")),
        @AttributeOverride(name = "active", column = @Column(name = "is_active"))
    })
    public List<CustomerContactEntity> contacts = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "crm_customer_addresses", joinColumns = @JoinColumn(name = "customer_id"))
    @AttributeOverrides({
        @AttributeOverride(name = "id", column = @Column(name = "address_id")),
        @AttributeOverride(name = "type", column = @Column(name = "address_type", length = 10)),
        @AttributeOverride(name = "address", column = @Column(name = "address", length = 255)),
        @AttributeOverride(name = "city", column = @Column(name = "city", length = 50)),
        @AttributeOverride(name = "state", column = @Column(name = "state", length = 50)),
        @AttributeOverride(name = "postalCode", column = @Column(name = "postal_code", length = 20)),
        @AttributeOverride(name = "country", column = @Column(name = "country", length = 50))
    })
    public List<CustomerAddressEntity> addresses = new ArrayList<>();

    @Embeddable
    public static class CustomerContactEntity {
        public String id;
        public String firstName;
        public String lastName;
        public String email;
        public String phone;
        public String jobTitle;
        public String department;
        public boolean primary;
        public boolean active;
    }

    @Embeddable
    public static class CustomerAddressEntity {
        public String id;
        public String type;
        public String address;
        public String city;
        public String state;
        public String postalCode;
        public String country;
    }
}