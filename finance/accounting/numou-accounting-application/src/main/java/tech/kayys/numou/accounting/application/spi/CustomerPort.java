
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Port for customer information from CRM context.
 */
public interface CustomerPort {

    /**
     * Validates customer exists and is active.
     */
    CompletionStage<Boolean> validateCustomer(UUID customerId);

    /**
     * Gets customer billing details.
     */
    CompletionStage<CustomerBillingDetails> getCustomerBillingDetails(UUID customerId);

    record CustomerBillingDetails(
        UUID customerId,
        String name,
        String email,
        String billingAddress,
        String taxId,
        String currencyCode
    ) {}
}