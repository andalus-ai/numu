package tech.kayys.payment.method.processor;

import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.payment.gateway.GatewayRequest;
import tech.kayys.payment.method.PaymentMethodContext;
import tech.kayys.payment.method.PaymentMethodType;

/**
 * Processor for Fintech/Paylater payments (Kredivo, Akulaku, Indodana)
 */
@ApplicationScoped
public class FintechProcessor extends AbstractPaymentMethodProcessor {
    
    @Override
    public PaymentMethodType getPaymentMethodType() {
        return PaymentMethodType.FINTECH;
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
        request.setDescription("Fintech/Paylater Payment");
        
        // Build customer info - critical for paylater approval
        GatewayRequest.CustomerInfo customer = new GatewayRequest.CustomerInfo();
        customer.setName(context.getCustomerName());
        customer.setEmail(context.getCustomerEmail());
        customer.setPhone(context.getCustomerPhone());
        request.setCustomer(customer);
        
        // Add items for paylater assessment
        if (context.getMetadata().containsKey("items")) {
            Object itemsObj = context.getMetadata().get("items");
            if (itemsObj instanceof GatewayRequest.ItemInfo[]) {
                request.setItems((GatewayRequest.ItemInfo[]) itemsObj);
            }
        }
        
        // Fintech-specific metadata
        Map<String, Object> metadata = new HashMap<>(context.getMetadata());
        if (context.getPaymentMethod() != null) {
            metadata.put("fintech_provider", context.getPaymentMethod().getCode());
        }
        
        // Add shipping info if available (required for some paylater providers)
        if (context.getMetadata().containsKey("shipping_address")) {
            metadata.put("shipping_address", context.getMetadata().get("shipping_address"));
        }
        
        request.setMetadata(metadata);
        
        return request;
    }
    
    private String mapToGatewayPaymentMethod(PaymentMethodType methodType) {
        if (methodType == null) {
            return "FINTECH";
        }
        
        switch (methodType) {
            case KREDIVO:
                return "KREDIVO";
            case AKULAKU:
                return "AKULAKU";
            case INDODANA:
                return "INDODANA";
            default:
                return "FINTECH";
        }
    }
}
