package tech.kayys.numou.crm.application.api.query;

import tech.kayys.numou.crm.domain.model.Customer;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Complete view of a customer.
 */
public record CustomerView(
        String customerId,
        String customerNumber,
        String companyName,
        String firstName,
        String lastName,
        String fullName,
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
        List<ContactView> contacts,
        List<AddressView> addresses,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        boolean active
) {

    public static CustomerView fromDomain(Customer customer) {
        return new CustomerView(
            customer.getId().toString(),
            customer.getCustomerNumber(),
            customer.getCompanyName(),
            customer.getFirstName(),
            customer.getLastName(),
            customer.getFirstName() + " " + customer.getLastName(),
            customer.getEmail(),
            customer.getPhone(),
            customer.getAddress(),
            customer.getCity(),
            customer.getState(),
            customer.getPostalCode(),
            customer.getCountry(),
            customer.getIndustry(),
            customer.getWebsite(),
            customer.getTaxId(),
            customer.getCurrencyCode(),
            customer.getPaymentTerms(),
            customer.getCreditLimit(),
            customer.getAccountStatus(),
            customer.getContacts().stream()
                .map(ContactView::fromDomain)
                .collect(Collectors.toList()),
            customer.getAddresses().stream()
                .map(AddressView::fromDomain)
                .collect(Collectors.toList()),
            customer.getNotes(),
            customer.getCreatedAt(),
            customer.getUpdatedAt(),
            customer.isActive()
        );
    }

    public record ContactView(
            String id,
            String firstName,
            String lastName,
            String fullName,
            String email,
            String phone,
            String jobTitle,
            String department,
            boolean primary,
            boolean active
    ) {
        public static ContactView fromDomain(Customer.CustomerContact contact) {
            return new ContactView(
                contact.getId(),
                contact.getFirstName(),
                contact.getLastName(),
                contact.getFullName(),
                contact.getEmail(),
                contact.getPhone(),
                contact.getJobTitle(),
                contact.getDepartment(),
                contact.isPrimary(),
                contact.isActive()
            );
        }
    }

    public record AddressView(
            String id,
            String type,
            String address,
            String city,
            String state,
            String postalCode,
            String country,
            String fullAddress,
            boolean billing,
            boolean shipping
    ) {
        public static AddressView fromDomain(Customer.CustomerAddress address) {
            return new AddressView(
                address.getId(),
                address.getType(),
                address.getAddress(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.getFullAddress(),
                address.isBilling(),
                address.isShipping()
            );
        }
    }
}