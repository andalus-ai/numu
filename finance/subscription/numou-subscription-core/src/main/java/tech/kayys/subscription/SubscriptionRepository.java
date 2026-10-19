package tech.kayys.billing.subscription;

import java.time.LocalDate;
import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class SubscriptionRepository implements PanacheRepository<Subscription> {
    public List<Subscription> findByBillingAccountId(Long billingAccountId) {
        return list("billingAccount.id", billingAccountId);
    }
    
    public List<Subscription> findActiveSubscriptionsByBillingAccountId(Long billingAccountId) {
        return list("billingAccount.id = ?1 AND status = ?2", 
                billingAccountId, Subscription.SubscriptionStatus.ACTIVE);
    }
    
    public List<Subscription> findExpiredSubscriptions() {
        return list("endDate <= ?1 AND status = ?2", 
                LocalDate.now(), Subscription.SubscriptionStatus.ACTIVE);
    }
}

