package tech.kayys.payment.gateway.provider;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import tech.kayys.payment.gateway.GatewayConfig;
import tech.kayys.payment.gateway.GatewayProvider;
import tech.kayys.payment.gateway.GatewayRequest;
import tech.kayys.payment.gateway.GatewayResponse;
import tech.kayys.payment.gateway.PaymentGatewayProvider;

/**
 * Xendit Payment Gateway Implementation
 * Supports: Virtual Accounts, E-Wallets, QRIS, Credit Cards, Paylater
 */
@ApplicationScoped
public class XenditGatewayProvider implements PaymentGatewayProvider {
    
    @ConfigProperty(name = "payment.gateway.xendit.secret-key")
    String secretKey;
    
    @ConfigProperty(name = "payment.gateway.xendit.public-key", defaultValue = "")
    String publicKey;
    
    @ConfigProperty(name = "payment.gateway.xendit.environment", defaultValue = "SANDBOX")
    String environment;
    
    @ConfigProperty(name = "payment.gateway.xendit.base-url", defaultValue = "https://api.xendit.co")
    String baseUrl;

    @Override
    public GatewayProvider getProvider() {
        return GatewayProvider.XENDIT;
    }

    @Override
    public boolean supportsPaymentMethod(String paymentMethodCode) {
        return true;
    }

    @Override
    public GatewayResponse createPayment(GatewayRequest request) {
        try {
            Map<String, Object> payload = buildXenditPayload(request);
            
            // Use appropriate endpoint based on payment method
            Response response = createPaymentRequest(payload, request.getPaymentMethod());
            
            if (response.getStatus() == 200 || response.getStatus() == 201) {
                Map<String, Object> responseBody = response.readEntity(Map.class);
                return parseXenditResponse(responseBody);
            } else {
                Map<String, Object> errorBody = response.readEntity(Map.class);
                return GatewayResponse.builder()
                    .success(false)
                    .statusCode(String.valueOf(response.getStatus()))
                    .errorMessage((String) errorBody.get("message"))
                    .errorCode((String) errorBody.get("error_code"))
                    .build();
            }
        } catch (Exception e) {
            return GatewayResponse.builder()
                .success(false)
                .errorMessage("Payment creation failed: " + e.getMessage())
                .errorCode("INTERNAL_ERROR")
                .build();
        }
    }

    @Override
    public GatewayResponse getPaymentStatus(String transactionId) {
        try {
            // Xendit client would be injected here in real implementation
            Response response = null; // xenditClient.getPaymentRequest(transactionId);
            
            if (response != null && (response.getStatus() == 200 || response.getStatus() == 201)) {
                Map<String, Object> responseBody = response.readEntity(Map.class);
                return parseXenditResponse(responseBody);
            } else {
                return GatewayResponse.builder()
                    .success(false)
                    .statusCode("404")
                    .errorMessage("Transaction not found")
                    .build();
            }
        } catch (Exception e) {
            return GatewayResponse.builder()
                .success(false)
                .errorMessage("Status check failed: " + e.getMessage())
                .errorCode("INTERNAL_ERROR")
                .build();
        }
    }

    @Override
    public GatewayResponse cancelPayment(String transactionId, String reason) {
        // Xendit doesn't support direct cancellation for all payment types
        // This would need custom implementation per payment method
        return GatewayResponse.builder()
            .success(false)
            .errorMessage("Cancellation not supported for this payment method")
            .errorCode("NOT_SUPPORTED")
            .build();
    }

    @Override
    public GatewayResponse refundPayment(String transactionId, String reason) {
        return refundPayment(transactionId, null, reason);
    }

    @Override
    public GatewayResponse refundPayment(String transactionId, BigDecimal amount, String reason) {
        try {
            XenditClient.RefundPayload payload = new XenditClient.RefundPayload();
            payload.payment_request_id = transactionId;
            payload.currency = "IDR";
            payload.reason = reason;
            
            if (amount != null) {
                payload.amount = amount.longValue();
            }
            
            // Xendit client would be injected here
            // Response response = xenditClient.refund(payload);
            
            return GatewayResponse.builder()
                .success(true)
                .transactionId(transactionId)
                .paymentStatus(GatewayResponse.PaymentStatus.REFUNDED)
                .build();
        } catch (Exception e) {
            return GatewayResponse.builder()
                .success(false)
                .errorMessage("Refund failed: " + e.getMessage())
                .errorCode("INTERNAL_ERROR")
                .build();
        }
    }

    @Override
    public GatewayConfig getConfig() {
        GatewayConfig config = new GatewayConfig();
        config.setProviderCode("XENDIT");
        config.setSecretKey(secretKey);
        config.setEnvironment(environment);
        config.setBaseUrl(baseUrl);
        return config;
    }

    @Override
    public boolean verifyCallback(String signature, String payload) {
        // Verify Xendit callback signature
        // Xendit uses x-callback-token header
        try {
            String expectedToken = Base64.getEncoder().encodeToString(
                secretKey.getBytes(StandardCharsets.UTF_8));
            return expectedToken.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }
    
    private Response createPaymentRequest(Map<String, Object> payload, String paymentMethod) {
        // In real implementation, inject and use XenditClient
        // This is a placeholder
        return null;
    }
    
    private Map<String, Object> buildXenditPayload(GatewayRequest request) {
        Map<String, Object> payload = new HashMap<>();
        
        payload.put("amount", request.getAmount().longValue());
        payload.put("currency", request.getCurrency());
        payload.put("reference_id", request.getExternalOrderId());
        payload.put("description", request.getDescription());
        
        // Customer information
        if (request.getCustomer() != null) {
            payload.put("customer", buildXenditCustomer(request.getCustomer()));
        }
        
        // Payment method specific configuration
        String paymentMethodType = getPaymentMethodType(request.getPaymentMethod());
        payload.put("payment_method", paymentMethodType);
        
        // Callback URLs
        Map<String, String> callbacks = new HashMap<>();
        if (request.getCallbackUrl() != null) {
            callbacks.put("success", request.getCallbackUrl());
        }
        payload.put("callback_url", request.getCallbackUrl());
        
        return payload;
    }
    
    private Map<String, String> buildXenditCustomer(GatewayRequest.CustomerInfo customer) {
        Map<String, String> xenditCustomer = new HashMap<>();
        xenditCustomer.put("given_names", customer.getName());
        xenditCustomer.put("email", customer.getEmail());
        xenditCustomer.put("mobile_number", customer.getPhone());
        return xenditCustomer;
    }
    
    private String getPaymentMethodType(String paymentMethod) {
        if (paymentMethod == null) {
            return "MULTI_CHANNEL";
        }
        
        switch (paymentMethod.toLowerCase()) {
            case "qris":
                return "QRPH";
            case "ovo":
                return "OVO";
            case "dana":
                return "DANA";
            case "gopay":
                return "GOPAY";
            case "shopeepay":
                return "SHOPEEPAY";
            case "kredivo":
                return "KREDIVO";
            case "akulaku":
                return "AKULAKU";
            case "bca_va":
            case "va_bca":
                return "BANK_TRANSFER";
            default:
                return "MULTI_CHANNEL";
        }
    }
    
    private GatewayResponse parseXenditResponse(Map<String, Object> responseBody) {
        GatewayResponse.Builder builder = GatewayResponse.builder();
        
        String id = (String) responseBody.get("id");
        String referenceId = (String) responseBody.get("reference_id");
        String status = (String) responseBody.get("status");
        String currency = (String) responseBody.get("currency");
        Number amount = (Number) responseBody.get("amount");
        String paymentMethod = (String) responseBody.get("payment_method");
        
        builder.transactionId(id)
               .externalTransactionId(referenceId)
               .currency(currency);
        
        if (amount != null) {
            builder.amount(new BigDecimal(amount.toString()));
        }
        
        // Parse status
        GatewayResponse.PaymentStatus paymentStatus = parseXenditStatus(status);
        builder.paymentStatus(paymentStatus);
        builder.success(paymentStatus == GatewayResponse.PaymentStatus.SUCCESS ||
                       paymentStatus == GatewayResponse.PaymentStatus.PENDING);
        
        // Parse payment method specific data
        if (responseBody.containsKey("qr_code")) {
            Map<String, String> qrCode = (Map<String, String>) responseBody.get("qr_code");
            builder.qrCodeString(qrCode.get("qr_string"));
            builder.qrCodeUrl(qrCode.get("url"));
        }
        
        if (responseBody.containsKey("virtual_account")) {
            Map<String, Object> va = (Map<String, Object>) responseBody.get("virtual_account");
            builder.virtualAccountNumber((String) va.get("account_number"));
        }
        
        return builder.build();
    }
    
    private GatewayResponse.PaymentStatus parseXenditStatus(String status) {
        if (status == null) {
            return GatewayResponse.PaymentStatus.FAILED;
        }
        
        switch (status) {
            case "SUCCEEDED":
                return GatewayResponse.PaymentStatus.SUCCESS;
            case "PENDING":
                return GatewayResponse.PaymentStatus.PENDING;
            case "FAILED":
            case "VOIDED":
                return GatewayResponse.PaymentStatus.FAILED;
            case "REFUNDED":
                return GatewayResponse.PaymentStatus.REFUNDED;
            default:
                return GatewayResponse.PaymentStatus.FAILED;
        }
    }
}
