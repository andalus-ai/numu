package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.crm.domain.identifier.LeadId;

/**
 * Command to create a new lead.
 */
public record CreateLeadCommand(
        LeadId leadId,
        String firstName,
        String lastName,
        String email,
        String phone,
        String company,
        String jobTitle,
        String industry,
        String source,
        String notes
) implements Command {

    public CreateLeadCommand {
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new IllegalArgumentException("First name cannot be empty");
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new IllegalArgumentException("Last name cannot be empty");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Source cannot be empty");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private LeadId leadId;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String company;
        private String jobTitle;
        private String industry;
        private String source;
        private String notes;

        public Builder leadId(LeadId leadId) {
            this.leadId = leadId;
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

        public Builder company(String company) {
            this.company = company;
            return this;
        }

        public Builder jobTitle(String jobTitle) {
            this.jobTitle = jobTitle;
            return this;
        }

        public Builder industry(String industry) {
            this.industry = industry;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public CreateLeadCommand build() {
            if (leadId == null) {
                leadId = LeadId.generate();
            }
            return new CreateLeadCommand(
                leadId, firstName, lastName, email, phone,
                company, jobTitle, industry, source, notes
            );
        }
    }
}