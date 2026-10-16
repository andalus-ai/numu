package tech.kayys.billing.subscription;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/subscriptions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SubscriptionResource {
    @Inject
    SubscriptionService subscriptionService;
    
    @GET
    @Path("/{id}")
    public Subscription getSubscription(@PathParam("id") Long id) {
        return subscriptionService.getSubscriptionById(id);
    }
    
    @GET
    @Path("/billing-account/{billingAccountId}")
    public List<Subscription> getSubscriptionsByBillingAccount(
            @PathParam("billingAccountId") Long billingAccountId) {
        return subscriptionService.getSubscriptionsByBillingAccountId(billingAccountId);
    }
    
    @GET
    @Path("/billing-account/{billingAccountId}/active")
    public List<Subscription> getActiveSubscriptionsByBillingAccount(
            @PathParam("billingAccountId") Long billingAccountId) {
        return subscriptionService.getActiveSubscriptionsByBillingAccountId(billingAccountId);
    }
    
    @POST
    @Path("/billing-account/{billingAccountId}/product/{productId}")
    @Transactional
    public Response createSubscription(
            @PathParam("billingAccountId") Long billingAccountId,
            @PathParam("productId") Long productId,
            Subscription subscription) {
        Subscription created = subscriptionService.createSubscription(subscription, billingAccountId, productId);
        return Response
            .status(Response.Status.CREATED)
            .entity(created)
            .build();
    }
    
    @PUT
    @Path("/{id}")
    @Transactional
    public Subscription updateSubscription(
            @PathParam("id") Long id, 
            Subscription subscription) {
        return subscriptionService.updateSubscription(id, subscription);
    }
    
    @PUT
    @Path("/{id}/cancel")
    @Transactional
    public Response cancelSubscription(@PathParam("id") Long id) {
        subscriptionService.cancelSubscription(id);
        return Response.ok().build();
    }
}