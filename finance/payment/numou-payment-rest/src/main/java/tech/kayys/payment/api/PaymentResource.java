package tech.kayys.payment;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import tech.kayys.payment.dto.CreatePaymentRequest;
import tech.kayys.payment.dto.PaymentMethodsResponse;
import tech.kayys.payment.dto.PaymentResponse;
import tech.kayys.payment.dto.RefundRequest;
import tech.kayys.payment.dto.RefundResponse;
import tech.kayys.payment.gateway.GatewayProvider;
import tech.kayys.payment.method.PaymentMethodType;
import tech.kayys.payment.service.PaymentGatewayRegistry;
import tech.kayys.payment.service.PaymentMethodRegistry;

/**
 * REST API for Payment operations
 */
@Path("/api/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {
    
    private static final Logger LOG = Logger.getLogger(PaymentResource.class);
    
    @Inject
    PaymentService paymentService;
    
    @Inject
    PaymentMethodRegistry methodRegistry;
    
    @Inject
    PaymentGatewayRegistry gatewayRegistry;
    
    /**
     * Get payment by ID
     */
    @GET
    @Path("/{id}")
    public PaymentResponse getPayment(@PathParam("id") Long id) {
        Payment payment = paymentService.getPaymentById(id);
        return PaymentResponse.fromEntity(payment);
    }
    
    /**
     * Get payment by transaction ID
     */
    @GET
    @Path("/transaction/{transactionId}")
    public PaymentResponse getPaymentByTransactionId(@PathParam("transactionId") String transactionId) {
        Payment payment = paymentService.getPaymentByTransactionId(transactionId);
        return PaymentResponse.fromEntity(payment);
    }
    
    /**
     * Get payments by invoice ID
     */
    @GET
    @Path("/invoice/{invoiceId}")
    public List<PaymentResponse> getPaymentsByInvoice(@PathParam("invoiceId") Long invoiceId) {
        List<Payment> payments = paymentService.getPaymentsByInvoiceId(invoiceId);
        return payments.stream()
            .map(PaymentResponse::fromEntity)
            .collect(Collectors.toList());
    }
    
    /**
     * Get payments by status
     */
    @GET
    @Path("/status/{status}")
    public List<PaymentResponse> getPaymentsByStatus(@PathParam("status") Payment.PaymentStatus status) {
        List<Payment> payments = paymentService.getPaymentsByStatus(status);
        return payments.stream()
            .map(PaymentResponse::fromEntity)
            .collect(Collectors.toList());
    }
    
    /**
     * Get pending payments
     */
    @GET
    @Path("/pending")
    public List<PaymentResponse> getPendingPayments() {
        List<Payment> payments = paymentService.getPendingPayments();
        return payments.stream()
            .map(PaymentResponse::fromEntity)
            .collect(Collectors.toList());
    }
    
    /**
     * Create and process payment
     */
    @POST
    @Transactional
    public Response createPayment(CreatePaymentRequest request) {
        try {
            LOG.infof("Creating payment for order %s with method %s", 
                request.getExternalOrderId(), request.getPaymentMethod());
            
            PaymentResponse response = paymentService.createAndProcessPayment(request);
            
            return Response
                .status(Response.Status.CREATED)
                .entity(response)
                .build();
        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid payment request: %s", e.getMessage());
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("INVALID_REQUEST", e.getMessage()))
                .build();
        } catch (Exception e) {
            LOG.errorf("Payment creation failed: %s", e.getMessage());
            return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("PAYMENT_FAILED", e.getMessage()))
                .build();
        }
    }
    
    /**
     * Verify payment status
     */
    @PUT
    @Path("/{id}/verify")
    @Transactional
    public PaymentResponse verifyPayment(@PathParam("id") Long id) {
        Payment payment = paymentService.verifyPayment(id);
        return PaymentResponse.fromEntity(payment);
    }
    
    /**
     * Cancel payment
     */
    @PUT
    @Path("/{id}/cancel")
    @Transactional
    public PaymentResponse cancelPayment(
            @PathParam("id") Long id,
            CancelRequest request) {
        String reason = request != null ? request.reason : "Cancelled by user";
        Payment payment = paymentService.cancelPayment(id, reason);
        return PaymentResponse.fromEntity(payment);
    }
    
    /**
     * Refund payment
     */
    @POST
    @Path("/{id}/refund")
    @Transactional
    public RefundResponse refundPayment(
            @PathParam("id") Long id,
            RefundRequest refundRequest) {
        return paymentService.refundPayment(id, refundRequest);
    }
    
    /**
     * Get available payment methods and gateway providers
     */
    @GET
    @Path("/methods")
    public PaymentMethodsResponse getAvailablePaymentMethods() {
        List<PaymentMethodType> methods = methodRegistry.getAvailablePaymentMethods();
        List<GatewayProvider> providers = gatewayRegistry.getAllProviders().stream()
            .map(provider -> provider.getProvider())
            .collect(Collectors.toList());
        
        return PaymentMethodsResponse.fromLists(methods, providers);
    }
    
    /**
     * Get available payment methods by type
     */
    @GET
    @Path("/methods/{type}")
    public PaymentMethodsResponse getPaymentMethodsByType(@PathParam("type") String type) {
        List<PaymentMethodType> methods;
        
        switch (type.toLowerCase()) {
            case "bank_transfer":
            case "transfer":
                methods = methodRegistry.getBankTransferProcessors().stream()
                    .map(p -> p.getPaymentMethodType())
                    .collect(Collectors.toList());
                break;
            case "qris":
                methods = methodRegistry.getQRISProcessors().stream()
                    .map(p -> p.getPaymentMethodType())
                    .collect(Collectors.toList());
                break;
            case "e_wallet":
            case "ewallet":
                methods = methodRegistry.getEWalletProcessors().stream()
                    .map(p -> p.getPaymentMethodType())
                    .collect(Collectors.toList());
                break;
            case "fintech":
                methods = methodRegistry.getFintechProcessors().stream()
                    .map(p -> p.getPaymentMethodType())
                    .collect(Collectors.toList());
                break;
            case "card":
                methods = methodRegistry.getCardProcessors().stream()
                    .map(p -> p.getPaymentMethodType())
                    .collect(Collectors.toList());
                break;
            default:
                methods = methodRegistry.getAvailablePaymentMethods();
        }
        
        List<GatewayProvider> providers = gatewayRegistry.getAllProviders().stream()
            .map(provider -> provider.getProvider())
            .collect(Collectors.toList());
        
        return PaymentMethodsResponse.fromLists(methods, providers);
    }
    
    /**
     * Webhook endpoint for payment gateway callbacks
     */
    @POST
    @Path("/webhook/{provider}")
    @Transactional
    public Response handleWebhook(
            @PathParam("provider") String provider,
            String payload,
            @QueryParam("signature") String signature) {
        try {
            LOG.infof("Received webhook from provider %s", provider);
            
            // Verify webhook signature
            var gatewayOpt = gatewayRegistry.getProvider(provider.toUpperCase());
            if (gatewayOpt.isPresent() && signature != null) {
                boolean verified = gatewayOpt.get().verifyCallback(signature, payload);
                if (!verified) {
                    LOG.warnf("Webhook signature verification failed for %s", provider);
                    return Response
                        .status(Response.Status.UNAUTHORIZED)
                        .entity(new ErrorResponse("INVALID_SIGNATURE", "Webhook signature verification failed"))
                        .build();
                }
            }
            
            // Process webhook payload
            // This would typically update payment status based on gateway callback
            LOG.infof("Webhook processed successfully for %s", provider);
            
            return Response.ok().entity(new WebhookResponse("SUCCESS", "Webhook processed")).build();
        } catch (Exception e) {
            LOG.errorf("Webhook processing failed: %s", e.getMessage());
            return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("WEBHOOK_ERROR", e.getMessage()))
                .build();
        }
    }
    
    // Inner classes for request/response
    public static class CancelRequest {
        public String reason;
    }
    
    public static class ErrorResponse {
        public String errorCode;
        public String message;
        
        public ErrorResponse(String errorCode, String message) {
            this.errorCode = errorCode;
            this.message = message;
        }
    }
    
    public static class WebhookResponse {
        public String status;
        public String message;
        
        public WebhookResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }
    }
}
