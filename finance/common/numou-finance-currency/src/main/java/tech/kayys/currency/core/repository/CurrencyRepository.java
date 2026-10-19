package tech.kayys.sy.currency.core.repository;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.sy.currency.core.domain.Currency;

@ApplicationScoped
public class CurrencyRepository implements PanacheRepository<Currency> {
    
    public Optional<Currency> findByCode(String code) {
        return find("code", code).firstResultOptional();
    }

    public List<Currency> findAllActive() {
        return find("active", true).list();
    }

    public boolean existsByCode(String code) {
        return count("code = ?1 and active = true", code) > 0;
    }
}
