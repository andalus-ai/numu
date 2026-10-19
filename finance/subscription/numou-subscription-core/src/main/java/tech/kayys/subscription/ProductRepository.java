package tech.kayys.billing.subscription;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class ProductRepository implements PanacheRepository<Product> {
}
