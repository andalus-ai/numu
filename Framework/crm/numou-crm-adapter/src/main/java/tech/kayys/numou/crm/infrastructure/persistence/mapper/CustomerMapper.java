package tech.kayys.numou.crm.infrastructure.persistence.mapper;

import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.crm.domain.model.Customer;
import tech.kayys.numou.crm.infrastructure.persistence.entity.CustomerEntity;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.stream.Collectors;

/**
 * Mapper between Customer domain model and persistence entity.
 */
@ApplicationScoped
public class CustomerMapper {

    public CustomerEntity toEntity(Customer customer) {
        if (customer == null) {
            return null;
        }

        CustomerEntity entity = new CustomerEntity();
        if (customer.getId() != null) {
            entity.id = customer.getId().getValue();
        }
        entity.customerNumber = customer.getCustomerNumber();
        entity.companyName = customer.getCompanyName();
        entity.firstName = customer.getFirstName();
        entity.lastName = customer.getLastName();
        entity.email = customer.getEmail();
        entity.phone = customer.getPhone();
        entity.address = customer.getAddress();
        entity.city = customer.getCity();
        entity.state = customer.getState();
        entity.postalCode = customer.getPostalCode();
        entity.country = customer.getCountry();
        entity.industry = customer.getIndustry();
        entity.website = customer.getWebsite();
        entity.taxId = customer.getTaxId();
        entity.currencyCode = customer.getCurrencyCode();
        entity.paymentTerms = customer.getPaymentTerms();
        entity.creditLimit = customer.getCreditLimit();
        entity.accountStatus = customer.getAccountStatus();
        entity.notes = customer.getNotes();
        entity.active = customer.isActive();
        entity.createdAt = customer.getCreatedAt();
        entity.updatedAt = customer.getUpdatedAt();
        entity.version = (long) customer.getVersion();

        return entity;
    }

    public Customer toDomain(CustomerEntity entity) {
        if (entity == null) {
            return null;
        }

        Customer customer = new Customer(CustomerId.of(entity.id));
        customer.setCustomerNumber(entity.customerNumber);
        customer.setCompanyName(entity.companyName);
        customer.setFirstName(entity.firstName);
        customer.setLastName(entity.lastName);
        customer.setEmail(entity.email);
        customer.setPhone(entity.phone);
        customer.setAddress(entity.address);
        customer.setCity(entity.city);
        customer.setState(entity.state);
        customer.setPostalCode(entity.postalCode);
        customer.setCountry(entity.country);
        customer.setIndustry(entity.industry);
        customer.setWebsite(entity.website);
        customer.setTaxId(entity.taxId);
        customer.setCurrencyCode(entity.currencyCode);
        customer.setPaymentTerms(entity.paymentTerms);
        customer.setCreditLimit(entity.creditLimit);
        customer.setAccountStatus(entity.accountStatus);
        customer.setNotes(entity.notes);
        customer.setActive(entity.active);
        customer.setCreatedAt(entity.createdAt);
        customer.setUpdatedAt(entity.updatedAt);
        customer.setVersion(entity.version != null ? entity.version.intValue() : 0);

        return customer;
    }
}
