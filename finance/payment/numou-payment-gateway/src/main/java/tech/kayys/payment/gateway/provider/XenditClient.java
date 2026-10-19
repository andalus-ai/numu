package tech.kayys.payment.gateway.provider;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import tech.kayys.payment.gateway.GatewayRequest;
import tech.kayys.payment.gateway.GatewayResponse;

/**
 * Xendit REST API Client
 * Documentation: https://developers.xendit.co/api-reference/
 */
@RegisterRestClient(configKey = "xendit")
@Path("/")
public interface XenditClient {
    
    /**
     * Create payment transaction
     */
    @POST
    @Path("v2/payment_requests")
    Response createPayment(GatewayRequest request);
    
    /**
     * Get payment request status
     */
    @GET
    @Path("payment_requests/{paymentRequestId}")
    Response getPaymentRequest(@PathParam("paymentRequestId") String paymentRequestId);
    
    /**
     * Create QRIS payment
     */
    @POST
    @Path("qr-phases")
    Response createQRIS(GatewayRequest request);
    
    /**
     * Create virtual account payment
     */
    @POST
    @Path("callback_url")
    Response createVirtualAccount(GatewayRequest request);
    
    /**
     * Create e-wallet charge (OVO, Dana, etc.)
     */
    @POST
    @Path("ewallets/charges")
    Response createEWalletCharge(GatewayRequest request);
    
    /**
     * Refund payment
     */
    @POST
    @Path("refunds")
    Response refund(RefundPayload payload);
    
    class RefundPayload {
        public String payment_request_id;
        public String currency;
        public Long amount;
        public String reason;
    }
}
