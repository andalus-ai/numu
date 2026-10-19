package tech.kayys.payment.gateway.provider;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import tech.kayys.payment.gateway.GatewayRequest;
import tech.kayys.payment.gateway.GatewayResponse;

/**
 * Midtrans REST API Client
 * Documentation: https://docs.midtrans.com/
 */
@RegisterRestClient(configKey = "midtrans")
@Path("/")
public interface MidtransClient {
    
    /**
     * Create payment transaction
     */
    @POST
    @Path("v2/charge")
    Response charge(Object request);
    
    /**
     * Get transaction status
     */
    @GET
    @Path("v2/{transactionId}/status")
    Response status(@PathParam("transactionId") String transactionId);
    
    /**
     * Approve challenge transaction
     */
    @POST
    @Path("v2/{transactionId}/approve")
    Response approve(@PathParam("transactionId") String transactionId);
    
    /**
     * Cancel transaction
     */
    @POST
    @Path("v2/{transactionId}/cancel")
    Response cancel(@PathParam("transactionId") String transactionId);
    
    /**
     * Refund transaction
     */
    @POST
    @Path("v2/{transactionId}/refund")
    Response refund(@PathParam("transactionId") String transactionId, RefundPayload payload);
    
    /**
     * Create QRIS payment
     */
    @POST
    @Path("v2/qris/generate")
    Response createQRIS(Object request);
    
    class RefundPayload {
        public String refund_key;
        public String amount;
        public String reason;
    }
}
