package tech.kayys.sy.currency.core.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.sy.currency.core.domain.ExchangeRate;

@ApplicationScoped
public class ExchangeRateRepository implements PanacheRepository<ExchangeRate> {
    
    public Optional<ExchangeRate> findLatestRate(String fromCurrencyCode, String toCurrencyCode) {
        return find("fromCurrency.code = ?1 and toCurrency.code = ?2 and active = true " +
                   "and (expiryDate is null or expiryDate >= current_date) " +
                   "and effectiveDate <= current_date " +
                   "order by effectiveDate desc", 
                   fromCurrencyCode, toCurrencyCode)
                .firstResultOptional();
    }

    public Optional<ExchangeRate> findRateForDate(String fromCurrencyCode, String toCurrencyCode, LocalDate date) {
        return find("fromCurrency.code = ?1 and toCurrency.code = ?2 and active = true " +
                   "and effectiveDate <= ?3 and (expiryDate is null or expiryDate >= ?3) " +
                   "order by effectiveDate desc", 
                   fromCurrencyCode, toCurrencyCode, date)
                .firstResultOptional();
    }

    public List<ExchangeRate> findRatesForCurrency(String currencyCode) {
        return find("(fromCurrency.code = ?1 or toCurrency.code = ?1) and active = true " +
                   "and (expiryDate is null or expiryDate >= current_date)", 
                   currencyCode)
                .list();
    }
}