package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.crm.domain.identifier.CustomerId;

/**
 * Command to add a contact to a customer.
 */
public record AddCustomerContactCommand(
        CustomerId customerId,
        String firstName,
        String lastName,
        String email,
        String phone,
        String jobTitle,
        String department,
        boolean primary
) implements Command {

    public AddCustomerContactCommand {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private CustomerId customerId;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String jobTitle;
        private String department;
        private boolean primary = false;

        public Builder customerId(CustomerId customerId) {
            this.customerId = customerId;
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

        public Builder jobTitle(String jobTitle) {
            this.jobTitle = jobTitle;
            return this;
        }

        public Builder department(String department) {
            this.department = department;
            return this;
        }

        public Builder primary(boolean primary) {
            this.primary = primary;
            return this;
        }

        public AddCustomerContactCommand build() {
            return new AddCustomerContactCommand(
                customerId, firstName, lastName, email, phone,
                jobTitle, department, primary
            );
        }
    }
}