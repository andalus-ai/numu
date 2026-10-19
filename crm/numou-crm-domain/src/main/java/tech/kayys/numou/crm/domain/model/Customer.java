package tech.kayys.numou.crm.domain.model;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.crm.domain.identifier.CustomerId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Customer aggregate root.
 * Represents a customer in the CRM.
 */
public final class Customer extends AbstractAggregateRoot<CustomerId> {
    
    private static final long serialVersionUID = 1L;
    
    private String customerNumber;
    private String companyName;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String industry;
    private String website;
    private String taxId;
    private String currencyCode;
    private String paymentTerms;
    private String creditLimit;
    private String accountStatus;
    private List<CustomerContact> contacts;
    private List<CustomerAddress> addresses;
    private String notes;
    private boolean active;

    public Customer(CustomerId id) {
        super(id);
        this.contacts = new ArrayList<>();
        this.addresses = new ArrayList<>();
        this.active = true;
    }

    private Customer() {
        super();
    }

    /**
     * Factory method to create a new customer.
     */
    public static Customer create(
            CustomerId id,
            String customerNumber,
            String companyName,
            String email,
            String currencyCode) {
        Customer customer = new Customer(id);
        customer.customerNumber = customerNumber;
        customer.companyName = companyName;
        customer.email = email;
        customer.currencyCode = currencyCode;
        return customer;
    }

    /**
     * Updates customer information.
     */
    public void update(String companyName, String phone, String address, String city, String state) {
        this.companyName = companyName;
        this.phone = phone;
        this.address = address;
        this.city = city;
        this.state = state;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds a contact to the customer.
     */
    public void addContact(CustomerContact contact) {
        if (contact.isPrimary()) {
            // If setting as primary, unset any existing primary
            contacts.forEach(c -> c.setPrimary(false));
        }
        contacts.add(contact);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Removes a contact from the customer.
     */
    public void removeContact(String contactId) {
        contacts.removeIf(c -> c.getId().equals(contactId));
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds an address to the customer.
     */
    public void addAddress(CustomerAddress address) {
        addresses.add(address);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Removes an address from the customer.
     */
    public void removeAddress(String addressId) {
        addresses.removeIf(a -> a.getId().equals(addressId));
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Gets the primary contact.
     */
    public CustomerContact getPrimaryContact() {
        return contacts.stream()
            .filter(CustomerContact::isPrimary)
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets the billing address.
     */
    public CustomerAddress getBillingAddress() {
        return addresses.stream()
            .filter(CustomerAddress::isBilling)
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets the shipping address.
     */
    public CustomerAddress getShippingAddress() {
        return addresses.stream()
            .filter(CustomerAddress::isShipping)
            .findFirst()
            .orElse(null);
    }

    // Getters
    public String getCustomerNumber() { return customerNumber; }
    public String getCompanyName() { return companyName; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public String getCountry() { return country; }
    public String getIndustry() { return industry; }
    public String getWebsite() { return website; }
    public String getTaxId() { return taxId; }
    public String getCurrencyCode() { return currencyCode; }
    public String getPaymentTerms() { return paymentTerms; }
    public String getCreditLimit() { return creditLimit; }
    public String getAccountStatus() { return accountStatus; }
    public List<CustomerContact> getContacts() { return Collections.unmodifiableList(contacts); }
    public List<CustomerAddress> getAddresses() { return Collections.unmodifiableList(addresses); }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCustomerNumber(String customerNumber) { this.customerNumber = customerNumber; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public void setActive(boolean active) { this.active = active; }
    public void setContacts(List<CustomerContact> contacts) { this.contacts = contacts != null ? new ArrayList<>(contacts) : new ArrayList<>(); }
    public void setAddresses(List<CustomerAddress> addresses) { this.addresses = addresses != null ? new ArrayList<>(addresses) : new ArrayList<>(); }

    public void setEmail(String email) {
        this.email = email;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setPhone(String phone) {
        this.phone = phone;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setAddress(String address) {
        this.address = address;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCity(String city) {
        this.city = city;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setState(String state) {
        this.state = state;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCountry(String country) {
        this.country = country;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setIndustry(String industry) {
        this.industry = industry;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setWebsite(String website) {
        this.website = website;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setPaymentTerms(String paymentTerms) {
        this.paymentTerms = paymentTerms;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCreditLimit(String creditLimit) {
        this.creditLimit = creditLimit;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setNotes(String notes) {
        this.notes = notes;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void deactivate() {
        this.active = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "Customer{" +
                "id=" + getId() +
                ", customerNumber='" + customerNumber + '\'' +
                ", companyName='" + companyName + '\'' +
                ", active=" + active +
                '}';
    }

    /**
     * Customer contact value object.
     */
    public static final class CustomerContact implements ValueObject {
        private static final long serialVersionUID = 1L;
        
        private final String id;
        private final String firstName;
        private final String lastName;
        private final String email;
        private final String phone;
        private final String jobTitle;
        private final String department;
        private boolean primary;
        private final boolean active;

        public CustomerContact(
                String id,
                String firstName,
                String lastName,
                String email,
                String phone,
                String jobTitle,
                String department,
                boolean primary,
                boolean active) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.phone = phone;
            this.jobTitle = jobTitle;
            this.department = department;
            this.primary = primary;
            this.active = active;
            validate();
        }

        @Override
        public void validate() {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Contact ID cannot be empty");
            }
            if (firstName == null || firstName.trim().isEmpty()) {
                throw new IllegalArgumentException("First name cannot be empty");
            }
            if (lastName == null || lastName.trim().isEmpty()) {
                throw new IllegalArgumentException("Last name cannot be empty");
            }
            if (email == null || email.trim().isEmpty()) {
                throw new IllegalArgumentException("Email cannot be empty");
            }
        }

        public String getId() { return id; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getFullName() { return firstName + " " + lastName; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public String getJobTitle() { return jobTitle; }
        public String getDepartment() { return department; }
        public boolean isPrimary() { return primary; }
        public boolean isActive() { return active; }

        public void setPrimary(boolean primary) {
            this.primary = primary;
        }

        @Override
        public String toString() {
            return "CustomerContact{" +
                    "id='" + id + '\'' +
                    ", fullName='" + getFullName() + '\'' +
                    ", email='" + email + '\'' +
                    ", primary=" + primary +
                    '}';
        }
    }

    /**
     * Customer address value object.
     */
    public static final class CustomerAddress implements ValueObject {
        private static final long serialVersionUID = 1L;
        
        private final String id;
        private final String type; // SHIPPING, BILLING, BOTH
        private final String address;
        private final String city;
        private final String state;
        private final String postalCode;
        private final String country;
        private final boolean isBilling;
        private final boolean isShipping;

        public CustomerAddress(
                String id,
                String type,
                String address,
                String city,
                String state,
                String postalCode,
                String country) {
            this.id = id;
            this.type = type;
            this.address = address;
            this.city = city;
            this.state = state;
            this.postalCode = postalCode;
            this.country = country;
            this.isBilling = "BILLING".equals(type) || "BOTH".equals(type);
            this.isShipping = "SHIPPING".equals(type) || "BOTH".equals(type);
            validate();
        }

        @Override
        public void validate() {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Address ID cannot be empty");
            }
            if (address == null || address.trim().isEmpty()) {
                throw new IllegalArgumentException("Address cannot be empty");
            }
            if (city == null || city.trim().isEmpty()) {
                throw new IllegalArgumentException("City cannot be empty");
            }
            if (country == null || country.trim().isEmpty()) {
                throw new IllegalArgumentException("Country cannot be empty");
            }
        }

        public String getId() { return id; }
        public String getType() { return type; }
        public String getAddress() { return address; }
        public String getCity() { return city; }
        public String getState() { return state; }
        public String getPostalCode() { return postalCode; }
        public String getCountry() { return country; }
        public boolean isBilling() { return isBilling; }
        public boolean isShipping() { return isShipping; }

        public String getFullAddress() {
            StringBuilder sb = new StringBuilder();
            sb.append(address);
            if (city != null && !city.isEmpty()) {
                sb.append(", ").append(city);
            }
            if (state != null && !state.isEmpty()) {
                sb.append(", ").append(state);
            }
            if (postalCode != null && !postalCode.isEmpty()) {
                sb.append(" ").append(postalCode);
            }
            if (country != null && !country.isEmpty()) {
                sb.append(", ").append(country);
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return "CustomerAddress{" +
                    "id='" + id + '\'' +
                    ", type='" + type + '\'' +
                    ", city='" + city + '\'' +
                    ", country='" + country + '\'' +
                    '}';
        }
    }
}