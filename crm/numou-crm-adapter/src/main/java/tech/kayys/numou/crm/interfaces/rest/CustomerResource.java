package tech.kayys.numou.crm.interfaces.rest;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.crm.application.api.CrmService;
import tech.kayys.numou.crm.application.api.command.AddCustomerContactCommand;
import tech.kayys.numou.crm.application.api.command.CreateCustomerCommand;
import tech.kayys.numou.crm.application.api.command.UpdateCustomerCommand;
import tech.kayys.numou.crm.application.api.query.CustomerView;
import tech.kayys.numou.crm.application.api.query.GetCustomerQuery;
import tech.kayys.numou.crm.application.api.query.SearchCustomersQuery;
import tech.kayys.numou.crm.domain.identifier.CustomerId;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * REST API for customer management.
 */
@Path("/api/v1/customers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Customer API", description = "Customer management endpoints")
public class CustomerResource {

    @Inject
    CrmService crmService;

    @POST
    @Operation(summary = "Create a new customer")
    @APIResponse(responseCode = "201", description = "Customer created")
    @APIResponse(responseCode = "400", description = "Invalid input")
    @APIResponse(responseCode = "409", description = "Customer already exists")
    public CompletionStage<Response> createCustomer(@Valid CreateCustomerRequest request) {
        CreateCustomerCommand command = CreateCustomerCommand.builder()
            .customerNumber(request.getCustomerNumber())
            .companyName(request.getCompanyName())
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .email(request.getEmail())
            .phone(request.getPhone())
            .address(request.getAddress())
            .city(request.getCity())
            .state(request.getState())
            .postalCode(request.getPostalCode())
            .country(request.getCountry())
            .industry(request.getIndustry())
            .website(request.getWebsite())
            .taxId(request.getTaxId())
            .currencyCode(request.getCurrencyCode() != null ? request.getCurrencyCode() : "USD")
            .paymentTerms(request.getPaymentTerms())
            .creditLimit(request.getCreditLimit())
            .accountStatus(request.getAccountStatus())
            .notes(request.getNotes())
            .build();

        return crmService.createCustomer(command)
            .thenApply(customerId -> Response
                .created(URI.create("/api/v1/customers/" + customerId.getValue()))
                .entity(new CreateCustomerResponse(customerId))
                .build()
            )
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get customer by ID")
    @APIResponse(responseCode = "200", description = "Customer found")
    @APIResponse(responseCode = "404", description = "Customer not found")
    public CompletionStage<Response> getCustomer(@PathParam("id") UUID id) {
        CustomerId customerId = CustomerId.of(id);
        GetCustomerQuery query = new GetCustomerQuery(customerId);

        return crmService.getCustomer(query)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build)
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a customer")
    @APIResponse(responseCode = "200", description = "Customer updated")
    @APIResponse(responseCode = "400", description = "Invalid input")
    @APIResponse(responseCode = "404", description = "Customer not found")
    public CompletionStage<Response> updateCustomer(
            @PathParam("id") UUID id,
            @Valid UpdateCustomerRequest request) {
        CustomerId customerId = CustomerId.of(id);

        UpdateCustomerCommand command = UpdateCustomerCommand.builder()
            .customerId(customerId)
            .companyName(request.getCompanyName())
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .email(request.getEmail())
            .phone(request.getPhone())
            .address(request.getAddress())
            .city(request.getCity())
            .state(request.getState())
            .postalCode(request.getPostalCode())
            .country(request.getCountry())
            .industry(request.getIndustry())
            .website(request.getWebsite())
            .taxId(request.getTaxId())
            .paymentTerms(request.getPaymentTerms())
            .creditLimit(request.getCreditLimit())
            .accountStatus(request.getAccountStatus())
            .notes(request.getNotes())
            .build();

        return crmService.updateCustomer(command)
            .thenApply(response -> Response.ok().build())
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @POST
    @Path("/{id}/contacts")
    @Operation(summary = "Add a contact to a customer")
    @APIResponse(responseCode = "200", description = "Contact added")
    @APIResponse(responseCode = "400", description = "Invalid input")
    @APIResponse(responseCode = "404", description = "Customer not found")
    public CompletionStage<Response> addContact(
            @PathParam("id") UUID id,
            @Valid AddContactRequest request) {
        CustomerId customerId = CustomerId.of(id);

        AddCustomerContactCommand command = AddCustomerContactCommand.builder()
            .customerId(customerId)
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .email(request.getEmail())
            .phone(request.getPhone())
            .jobTitle(request.getJobTitle())
            .department(request.getDepartment())
            .primary(request.isPrimary())
            .build();

        return crmService.addCustomerContact(command)
            .thenApply(response -> Response.ok().build())
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @GET
    @Operation(summary = "Search customers")
    @APIResponse(responseCode = "200", description = "Search results")
    public CompletionStage<Response> searchCustomers(
            @QueryParam("companyName") String companyName,
            @QueryParam("email") String email,
            @QueryParam("industry") String industry,
            @QueryParam("city") String city,
            @QueryParam("country") String country,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        SearchCustomersQuery query = new SearchCustomersQuery(
            companyName,
            email,
            industry,
            city,
            country,
            page,
            size
        );

        return crmService.searchCustomers(query)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    // =========================================================================
    // Request/Response DTOs
    // =========================================================================

    public static class CreateCustomerRequest {
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
        private String notes;

        // Getters and setters
        public String getCustomerNumber() { return customerNumber; }
        public void setCustomerNumber(String customerNumber) { this.customerNumber = customerNumber; }
        public String getCompanyName() { return companyName; }
        public void setCompanyName(String companyName) { this.companyName = companyName; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getPostalCode() { return postalCode; }
        public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
        public String getIndustry() { return industry; }
        public void setIndustry(String industry) { this.industry = industry; }
        public String getWebsite() { return website; }
        public void setWebsite(String website) { this.website = website; }
        public String getTaxId() { return taxId; }
        public void setTaxId(String taxId) { this.taxId = taxId; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public String getPaymentTerms() { return paymentTerms; }
        public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }
        public String getCreditLimit() { return creditLimit; }
        public void setCreditLimit(String creditLimit) { this.creditLimit = creditLimit; }
        public String getAccountStatus() { return accountStatus; }
        public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class UpdateCustomerRequest {
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

        // Getters and setters
        public String getCompanyName() { return companyName; }
        public void setCompanyName(String companyName) { this.companyName = companyName; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getPostalCode() { return postalCode; }
        public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
        public String getIndustry() { return industry; }
        public void setIndustry(String industry) { this.industry = industry; }
        public String getWebsite() { return website; }
        public void setWebsite(String website) { this.website = website; }
        public String getTaxId() { return taxId; }
        public void setTaxId(String taxId) { this.taxId = taxId; }
        public String getPaymentTerms() { return paymentTerms; }
        public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }
        public String getCreditLimit() { return creditLimit; }
        public void setCreditLimit(String creditLimit) { this.creditLimit = creditLimit; }
        public String getAccountStatus() { return accountStatus; }
        public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class AddContactRequest {
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String jobTitle;
        private String department;
        private boolean primary;

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getJobTitle() { return jobTitle; }
        public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
        public boolean isPrimary() { return primary; }
        public void setPrimary(boolean primary) { this.primary = primary; }
    }

    public static class CreateCustomerResponse {
        private final String customerId;

        public CreateCustomerResponse(CustomerId customerId) {
            this.customerId = customerId.toString();
        }

        public String getCustomerId() { return customerId; }
    }
}