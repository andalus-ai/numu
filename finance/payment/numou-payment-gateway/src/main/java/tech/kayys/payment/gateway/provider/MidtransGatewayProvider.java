package tech.kayys.payment.gateway.provider;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import tech.kayys.payment.gateway.GatewayConfig;
import tech.kayys.payment.gateway.GatewayProvider;
import tech.kayys.payment.gateway.GatewayRequest;
import tech.kayys.payment.gateway.GatewayResponse;
import tech.kayys.payment.gateway.PaymentGatewayProvider;

/**
 * Midtrans Payment Gateway Implementation
 * Supports: Credit Card, Bank Transfer, E-Wallet, QRIS, Paylater
 */
@ApplicationScoped
public class MidtransGatewayProvider implements PaymentGatewayProvider {
    
    @Inject
    @RestClient
    MidtransClient midtransClient;
    
    @ConfigProperty(name = "payment.gateway.midtrans.server-key")
    String serverKey;
    
    @ConfigProperty(name = "payment.gateway.midtrans.client-key", defaultValue = "")
    String clientKey;
    
    @ConfigProperty(name = "payment.gateway.midtrans.merchant-id", defaultValue = "")
    String merchantId;
    
    @ConfigProperty(name = "payment.gateway.midtrans.environment", defaultValue = "SANDBOX")
    String environment;
    
    @ConfigProperty(name = "payment.gateway.midtrans.base-url", defaultValue = "https://api.sandbox.midtrans.com")
    String baseUrl;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public GatewayProvider getProvider() {
        return GatewayProvider.MIDTRANS;
    }

    @Override
    public boolean supportsPaymentMethod(String paymentMethodCode) {
        // Midtrans supports most Indonesian payment methods
        return true;
    }

    @Override
    public GatewayResponse createPayment(GatewayRequest request) {
        try {
            Map<String, Object> payload = buildMidtransPayload(request);
            
            Response response = midtransClient.charge(payload);
            
            if (response.getStatus() == 200 || response.getStatus() == 201) {
                Map<String, Object> responseBody = response.readEntity(Map.class);
                return parseMidtransResponse(responseBody);
            } else {
                Map<String, Object> errorBody = response.readEntity(Map.class);
                return GatewayResponse.builder()
                    .success(false)
                    .statusCode(String.valueOf(response.getStatus()))
                    .errorMessage((String) errorBody.get("error_message"))
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
            Response response = midtransClient.status(transactionId);
            
            if (response.getStatus() == 200 || response.getStatus() == 201) {
                Map<String, Object> responseBody = response.readEntity(Map.class);
                return parseMidtransResponse(responseBody);
            } else {
                return GatewayResponse.builder()
                    .success(false)
                    .statusCode(String.valueOf(response.getStatus()))
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
        try {
            Response response = midtransClient.cancel(transactionId);
            
            if (response.getStatus() == 200 || response.getStatus() == 201) {
                Map<String, Object> responseBody = response.readEntity(Map.class);
                return parseMidtransResponse(responseBody);
            } else {
                return GatewayResponse.builder()
                    .success(false)
                    .statusCode(String.valueOf(response.getStatus()))
                    .errorMessage("Failed to cancel transaction")
                    .build();
            }
        } catch (Exception e) {
            return GatewayResponse.builder()
                .success(false)
                .errorMessage("Cancellation failed: " + e.getMessage())
                .errorCode("INTERNAL_ERROR")
                .build();
        }
    }

    @Override
    public GatewayResponse refundPayment(String transactionId, String reason) {
        return refundPayment(transactionId, null, reason);
    }

    @Override
    public GatewayResponse refundPayment(String transactionId, BigDecimal amount, String reason) {
        try {
            MidtransClient.RefundPayload payload = new MidtransClient.RefundPayload();
            payload.refund_key = "refund-" + System.currentTimeMillis();
            payload.reason = reason;
            
            if (amount != null) {
                payload.amount = amount.toString();
            }
            
            Response response = midtransClient.refund(transactionId, payload);
            
            if (response.getStatus() == 200 || response.getStatus() == 201) {
                Map<String, Object> responseBody = response.readEntity(Map.class);
                return parseMidtransResponse(responseBody);
            } else {
                Map<String, Object> errorBody = response.readEntity(Map.class);
                return GatewayResponse.builder()
                    .success(false)
                    .statusCode(String.valueOf(response.getStatus()))
                    .errorMessage((String) errorBody.get("error_message"))
                    .build();
            }
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
        config.setProviderCode("MIDTRANS");
        config.setApiKey(serverKey);
        config.setEnvironment(environment);
        config.setBaseUrl(baseUrl);
        config.setMerchantId(merchantId);
        return config;
    }

    @Override
    public boolean verifyCallback(String signature, String payload) {
        // Verify Midtrans webhook signature
        // SHA512 hash of (transactionId + statusCode + merchantKey + amount)
        try {
            String expectedSignature = generateSignature(payload, serverKey);
            return expectedSignature.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }
    
    private Map<String, Object> buildMidtransPayload(GatewayRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_details", buildTransactionDetails(request));
        payload.put("customer_details", buildCustomerDetails(request));
        payload.put("item_details", buildItemDetails(request));
        
        // Add payment method specific parameters
        String paymentMethod = request.getPaymentMethod();
        if (paymentMethod != null) {
            addPaymentMethodParams(payload, paymentMethod, request);
        }
        
        return payload;
    }
    
    private Map<String, Object> buildTransactionDetails(GatewayRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("order_id", request.getExternalOrderId());
        details.put("gross_amount", request.getAmount().longValue());
        return details;
    }
    
    private Map<String, Object> buildCustomerDetails(GatewayRequest request) {
        Map<String, Object> details = new HashMap<>();
        
        if (request.getCustomer() != null) {
            GatewayRequest.CustomerInfo customer = request.getCustomer();
            details.put("first_name", customer.getName());
            details.put("email", customer.getEmail());
            details.put("phone", customer.getPhone());
        }
        
        return details;
    }
    
    private java.util.List<Map<String, Object>> buildItemDetails(GatewayRequest request) {
        java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
        
        if (request.getItems() != null) {
            for (GatewayRequest.ItemInfo item : request.getItems()) {
                Map<String, Object> itemDetail = new HashMap<>();
                itemDetail.put("id", item.getId());
                itemDetail.put("price", item.getPrice().longValue());
                itemDetail.put("quantity", item.getQuantity());
                itemDetail.put("name", item.getName());
                items.add(itemDetail);
            }
        }
        
        return items;
    }
    
    private void addPaymentMethodParams(Map<String, Object> payload, String paymentMethod, GatewayRequest request) {
        switch (paymentMethod.toLowerCase()) {
            case "credit_card":
                Map<String, Object> cardParams = new HashMap<>();
                cardParams.put("secure", true);
                payload.put("credit_card", cardParams);
                break;
            case "gopay":
                Map<String, Object> gopayParams = new HashMap<>();
                gopayParams.put("enable_callback", true);
                gopayParams.put("callback_url", request.getCallbackUrl());
                payload.put("gopay", gopayParams);
                break;
            case "qris":
                // QRIS is default for Midtrans
                break;
            default:
                // Bank transfer and other methods
                break;
        }
    }
    
    private GatewayResponse parseMidtransResponse(Map<String, Object> responseBody) {
        GatewayResponse.Builder builder = GatewayResponse.builder();
        
        String statusCode = (String) responseBody.get("status_code");
        String transactionId = (String) responseBody.get("transaction_id");
        String orderId = (String) responseBody.get("order_id");
        String transactionStatus = (String) responseBody.get("transaction_status");
        String fraudStatus = (String) responseBody.get("fraud_status");
        String grossAmount = (String) responseBody.get("gross_amount");
        String paymentType = (String) responseBody.get("payment_type");
        
        builder.transactionId(transactionId)
               .externalTransactionId(orderId)
               .statusCode(statusCode);
        
        // Parse payment status
        GatewayResponse.PaymentStatus status = parseTransactionStatus(transactionStatus, fraudStatus);
        builder.paymentStatus(status);
        
        // Set amount
        if (grossAmount != null) {
            try {
                builder.amount(new BigDecimal(grossAmount));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        // Set payment URL for redirect payments
        if (responseBody.containsKey("redirect_url")) {
            builder.paymentUrl((String) responseBody.get("redirect_url"));
        }
        
        // Set QRIS code
        if (responseBody.containsKey("qr_string")) {
            builder.qrCodeString((String) responseBody.get("qr_string"));
        }
        
        // Set VA number for bank transfer
        if (responseBody.containsKey("va_numbers")) {
            java.util.List<Map<String, String>> vaNumbers = 
                (java.util.List<Map<String, String>>) responseBody.get("va_numbers");
            if (!vaNumbers.isEmpty()) {
                builder.virtualAccountNumber(vaNumbers.get(0).get("va_number"));
                builder.bankCode(vaNumbers.get(0).get("bank"));
            }
        }
        
        // Set expiry time
        if (responseBody.containsKey("expiry_time")) {
            try {
                LocalDateTime expiryTime = LocalDateTime.parse(
                    (String) responseBody.get("expiry_time"), DATE_FORMATTER);
                builder.expiryTime(expiryTime);
            } catch (Exception e) {
                // Ignore
            }
        }
        
        // Check success
        builder.success(status == GatewayResponse.PaymentStatus.SUCCESS || 
                       status == GatewayResponse.PaymentStatus.PENDING);
        
        return builder.build();
    }
    
    private GatewayResponse.PaymentStatus parseTransactionStatus(String transactionStatus, String fraudStatus) {
        if (transactionStatus == null) {
            return GatewayResponse.PaymentStatus.FAILED;
        }
        
        switch (transactionStatus) {
            case "capture":
            case "settlement":
                return GatewayResponse.PaymentStatus.SUCCESS;
            case "pending":
                return GatewayResponse.PaymentStatus.PENDING;
            case "deny":
            case "cancel":
            case "expire":
                return GatewayResponse.PaymentStatus.FAILED;
            case "refund":
                return GatewayResponse.PaymentStatus.REFUNDED;
            case "challenge":
                return GatewayResponse.PaymentStatus.CHALLENGE;
            default:
                return GatewayResponse.PaymentStatus.FAILED;
        }
    }
    
    private String generateSignature(String payload, String serverKey) {
        try {
            String stringToSign = payload + serverKey;
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(stringToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return null;
        }
    }
}
