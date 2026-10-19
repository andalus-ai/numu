package tech.kayys.billing.subscription;

import java.util.List;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
class SubscriptionService {
    @Inject
    SubscriptionRepository subscriptionRepository;
    
    @Inject
    BillingAccountRepository billingAccountRepository;
    
    @Inject
    ProductRepository productRepository;
    
    @Transactional
    public Subscription createSubscription(Subscription subscription, Long billingAccountId, Long productId) {
        BillingAccount billingAccount = billingAccountRepository.findById(billingAccountId);
        if (billingAccount == null) {
            throw new NotFoundException("Billing account not found");
        }
        
        Product product = productRepository.findById(productId);
        if (product == null) {
            throw new NotFoundException("Product not found");
        }
        
        subscription.billingAccount = billingAccount;
        subscription.product = product;
        subscription.billingCycle = billingAccount.billingCycle;
        
        subscriptionRepository.persist(subscription);
        return subscription;
    }
    
    @Transactional
    public Subscription updateSubscription(Long id, Subscription updatedSubscription) {
        Subscription existingSubscription = subscriptionRepository.findById(id);
        if (existingSubscription == null) {
            throw new NotFoundException("Subscription not found");
        }
        
        existingSubscription.quantity = updatedSubscription.quantity;
        existingSubscription.negotiatedPrice = updatedSubscription.negotiatedPrice;
        existingSubscription.endDate = updatedSubscription.endDate;
        existingSubscription.status = updatedSubscription.status;
        
        return existingSubscription;
    }
    
    public Subscription getSubscriptionById(Long id) {
        Subscription subscription = subscriptionRepository.findById(id);
        if (subscription == null) {
            throw new NotFoundException("Subscription not found");
        }
        return subscription;
    }
    
    public List<Subscription> getSubscriptionsByBillingAccountId(Long billingAccountId) {
        return subscriptionRepository.findByBillingAccountId(billingAccountId);
    }
    
    public List<Subscription> getActiveSubscriptionsByBillingAccountId(Long billingAccountId) {
        return subscriptionRepository.findActiveSubscriptionsByBillingAccountId(billingAccountId);
    }
    
    @Transactional
    public void cancelSubscription(Long id) {
        Subscription subscription = subscriptionRepository.findById(id);
        if (subscription == null) {
            throw new NotFoundException("Subscription not found");
        }
        subscription.status = Subscription.SubscriptionStatus.CANCELED;
    }
    
    @Scheduled(cron = "0 0 0 * * ?") // Run once every day at midnight
    @Transactional
    public void checkExpiredSubscriptions() {
        List<Subscription> expiredSubscriptions = subscriptionRepository.findExpiredSubscriptions();
        for (Subscription subscription : expiredSubscriptions) {
            subscription.status = Subscription.SubscriptionStatus.EXPIRED;
        }
    }
}

