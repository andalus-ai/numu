package tech.kayys.payment.method.processor;

import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.payment.gateway.GatewayRequest;
import tech.kayys.payment.method.PaymentMethodContext;
import tech.kayys.payment.method.PaymentMethodType;

/**
 * Processor for Retail/Over-the-Counter payments (Alfamart, Indomaret)
 */
@ApplicationScoped
public class RetailProcessor extends AbstractPaymentMethodProcessor {
    
    @Override
    public PaymentMethodType getPaymentMethodType() {
        return PaymentMethodType.ALFAMART;
    }
    
    @Override
    protected GatewayRequest buildGatewayRequest(PaymentMethodContext context) {
        GatewayRequest request = new GatewayRequest();
        request.setTransactionId(context.getTransactionId());
        request.setExternalOrderId(context.getExternalOrderId());
        request.setAmount(context.getAmount());
        request.setCurrency(context.getCurrency());
        request.setPaymentMethod(mapToGatewayPaymentMethod(context.getPaymentMethod()));
        request.setCallbackUrl(context.getCallbackUrl());
        request.setReturnUrl(context.getReturnUrl());
        request.setDescription("Retail Payment");
        
        // Build customer info
        GatewayRequest.CustomerInfo customer = new GatewayRequest.CustomerInfo();
        customer.setName(context.getCustomerName());
        customer.setEmail(context.getCustomerEmail());
        customer.setPhone(context.getCustomerPhone());
        request.setCustomer(customer);
        
        // Add items for retail payment slip
        if (context.getMetadata().containsKey("items")) {
            Object itemsObj = context.getMetadata().get("items");
            if (itemsObj instanceof GatewayRequest.ItemInfo[]) {
                request.setItems((GatewayRequest.ItemInfo[]) itemsObj);
            }
        }
        
        // Retail-specific metadata
        Map<String, Object> metadata = new HashMap<>(context.getMetadata());
        if (context.getPaymentMethod() != null) {
            metadata.put("store_type", context.getPaymentMethod().getCode());
        }
        request.setMetadata(metadata);
        
        return request;
    }
    
    private String mapToGatewayPaymentMethod(PaymentMethodType methodType) {
        if (methodType == null) {
            return "RETAIL";
        }
        
        switch (methodType) {
            case ALFAMART:
                return "ALFAMART";
            case INDOMARET:
                return "INDOMARET";
            default:
                return "RETAIL";
        }
    }
}
