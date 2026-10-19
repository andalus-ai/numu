package tech.kayys.payment.method.processor;

import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.payment.gateway.GatewayRequest;
import tech.kayys.payment.method.PaymentMethodContext;
import tech.kayys.payment.method.PaymentMethodType;

/**
 * Processor for E-Wallet payments (OVO, GoPay, DANA, etc.)
 */
@ApplicationScoped
public class EWalletProcessor extends AbstractPaymentMethodProcessor {
    
    @Override
    public PaymentMethodType getPaymentMethodType() {
        return PaymentMethodType.E_WALLET;
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
        request.setDescription("E-Wallet Payment");
        
        // Build customer info - required for e-wallets
        GatewayRequest.CustomerInfo customer = new GatewayRequest.CustomerInfo();
        customer.setName(context.getCustomerName());
        customer.setEmail(context.getCustomerEmail());
        customer.setPhone(context.getCustomerPhone());
        request.setCustomer(customer);
        
        // E-wallet specific metadata
        Map<String, Object> metadata = new HashMap<>(context.getMetadata());
        if (context.getPaymentMethod() != null) {
            metadata.put("wallet_type", context.getPaymentMethod().getCode());
        }
        request.setMetadata(metadata);
        
        return request;
    }
    
    private String mapToGatewayPaymentMethod(PaymentMethodType methodType) {
        if (methodType == null) {
            return "E_WALLET";
        }
        
        switch (methodType) {
            case OVO:
                return "OVO";
            case GOPAY:
                return "GOPAY";
            case DANA:
                return "DANA";
            case LINKAJA:
                return "LINKAJA";
            case SHOPEEPAY:
                return "SHOPEEPAY";
            default:
                return "E_WALLET";
        }
    }
}
