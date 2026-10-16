package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.crm.domain.identifier.CustomerId;

/**
 * Command to update an existing customer.
 */
public record UpdateCustomerCommand(
        CustomerId customerId,
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
        String paymentTerms,
        String creditLimit,
        String accountStatus,
        String notes
) implements Command {

    public UpdateCustomerCommand {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        if (companyName == null || companyName.trim().isEmpty()) {
            throw new IllegalArgumentException("Company name cannot be empty");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private CustomerId customerId;
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
        private String paymentTerms;
        private String creditLimit;
        private String accountStatus;
        private String notes;

        public Builder customerId(CustomerId customerId) {
            this.customerId = customerId;
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

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public UpdateCustomerCommand build() {
            return new UpdateCustomerCommand(
                customerId, companyName, firstName, lastName, email,
                phone, address, city, state, postalCode, country,
                industry, website, taxId, paymentTerms, creditLimit,
                accountStatus, notes
            );
        }
    }
}