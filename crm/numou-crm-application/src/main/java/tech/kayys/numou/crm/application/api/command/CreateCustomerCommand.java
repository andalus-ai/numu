package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.crm.domain.identifier.CustomerId;

import java.util.List;

/**
 * Command to create a new customer.
 */
public record CreateCustomerCommand(
        CustomerId customerId,
        String customerNumber,
        String companyName,
        String firstName,
        String lastName,
        String email,
        String phone,
        String address,
        String city,
        String state,
        String postalCode,
        String country,
        String industry,
        String website,
        String taxId,
        String currencyCode,
        String paymentTerms,
        String creditLimit,
        String accountStatus,
        List<CustomerContactCommand> contacts,
        List<CustomerAddressCommand> addresses,
        String notes
) implements Command {

    public CreateCustomerCommand {
        if (companyName == null || companyName.trim().isEmpty()) {
            throw new IllegalArgumentException("Company name cannot be empty");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency code cannot be empty");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private CustomerId customerId;
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
        private String currencyCode = "USD";
        private String paymentTerms;
        private String creditLimit;
        private String accountStatus = "ACTIVE";
        private List<CustomerContactCommand> contacts;
        private List<CustomerAddressCommand> addresses;
        private String notes;

        public Builder customerId(CustomerId customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder customerNumber(String customerNumber) {
            this.customerNumber = customerNumber;
            return this;
        }

        public Builder companyName(String companyName) {
            this.companyName = companyName;
            return this;
        }

        public Builder firstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public Builder lastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public Builder address(String address) {
            this.address = address;
            return this;
        }

        public Builder city(String city) {
            this.city = city;
            return this;
        }

        public Builder state(String state) {
            this.state = state;
            return this;
        }

        public Builder postalCode(String postalCode) {
            this.postalCode = postalCode;
            return this;
        }

        public Builder country(String country) {
            this.country = country;
            return this;
        }

        public Builder industry(String industry) {
            this.industry = industry;
            return this;
        }

        public Builder website(String website) {
            this.website = website;
            return this;
        }

        public Builder taxId(String taxId) {
            this.taxId = taxId;
            return this;
        }

        public Builder currencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
            return this;
        }

        public Builder paymentTerms(String paymentTerms) {
            this.paymentTerms = paymentTerms;
            return this;
        }

        public Builder creditLimit(String creditLimit) {
            this.creditLimit = creditLimit;
            return this;
        }

        public Builder accountStatus(String accountStatus) {
            this.accountStatus = accountStatus;
            return this;
        }

        public Builder contacts(List<CustomerContactCommand> contacts) {
            this.contacts = contacts;
            return this;
        }

        public Builder addresses(List<CustomerAddressCommand> addresses) {
            this.addresses = addresses;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public CreateCustomerCommand build() {
            if (customerId == null) {
                customerId = CustomerId.generate();
            }
            if (customerNumber == null) {
                customerNumber = "CUST-" + System.currentTimeMillis();
            }
            return new CreateCustomerCommand(
                customerId, customerNumber, companyName, firstName, lastName,
                email, phone, address, city, state, postalCode, country,
                industry, website, taxId, currencyCode, paymentTerms,
                creditLimit, accountStatus, contacts, addresses, notes
            );
        }
    }

    public record CustomerContactCommand(
            String firstName,
            String lastName,
            String email,
            String phone,
            String jobTitle,
            String department,
            boolean primary
    ) {
        public CustomerContactCommand {
            if (firstName == null || firstName.trim().isEmpty()) {
                throw new IllegalArgumentException("Contact first name cannot be empty");
            }
            if (lastName == null || lastName.trim().isEmpty()) {
                throw new IllegalArgumentException("Contact last name cannot be empty");
            }
            if (email == null || email.trim().isEmpty()) {
                throw new IllegalArgumentException("Contact email cannot be empty");
            }
        }
    }

    public record CustomerAddressCommand(
            String type,
            String address,
            String city,
            String state,
            String postalCode,
            String country
    ) {
        public CustomerAddressCommand {
            if (type == null || type.trim().isEmpty()) {
                throw new IllegalArgumentException("Address type cannot be empty");
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
    }
}