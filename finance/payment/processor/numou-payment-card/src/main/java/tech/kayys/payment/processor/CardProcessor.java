package tech.kayys.payment.method.processor;

import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.payment.gateway.GatewayRequest;
import tech.kayys.payment.method.PaymentMethodContext;
import tech.kayys.payment.method.PaymentMethodType;

/**
 * Processor for Card payments (Credit/Debit)
 */
@ApplicationScoped
public class CardProcessor extends AbstractPaymentMethodProcessor {
    
    @Override
    public PaymentMethodType getPaymentMethodType() {
        return PaymentMethodType.CREDIT_CARD;
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
        request.setDescription("Card Payment");
        
        // Build customer info
        GatewayRequest.CustomerInfo customer = new GatewayRequest.CustomerInfo();
        customer.setName(context.getCustomerName());
        customer.setEmail(context.getCustomerEmail());
        customer.setPhone(context.getCustomerPhone());
        request.setCustomer(customer);
        
        // Card-specific metadata
        Map<String, Object> metadata = new HashMap<>(context.getMetadata());
        
        // Add installment info if available
        if (context.getMetadata().containsKey("installment_term")) {
            metadata.put("installment", buildInstallmentParams(
                context.getMetadata().get("installment_term"),
                context.getMetadata().get("installment_bank")
            ));
        }
        
        // Add 3DS secure flag
        metadata.put("secure", true);
        
        request.setMetadata(metadata);
        
        return request;
    }
    
    private String mapToGatewayPaymentMethod(PaymentMethodType methodType) {
        if (methodType == null) {
            return "CREDIT_CARD";
        }
        
        switch (methodType) {
            case CREDIT_CARD:
                return "CREDIT_CARD";
            case DEBIT_CARD:
                return "DEBIT_CARD";
            default:
                return "CREDIT_CARD";
        }
    }
    
    private Map<String, Object> buildInstallmentParams(Object termObj, Object bankObj) {
        Map<String, Object> installment = new HashMap<>();
        if (termObj != null) {
            installment.put("term", termObj.toString());
        }
        if (bankObj != null) {
            installment.put("bank", bankObj.toString());
        }
        return installment;
    }
}
