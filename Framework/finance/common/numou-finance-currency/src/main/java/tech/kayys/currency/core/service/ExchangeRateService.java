package tech.kayys.sy.currency.core.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.sy.currency.core.domain.Currency;
import tech.kayys.sy.currency.core.domain.ExchangeRate;
import tech.kayys.sy.currency.core.dto.CurrencyConversionRequest;
import tech.kayys.sy.currency.core.dto.CurrencyConversionResult;
import tech.kayys.sy.currency.core.dto.ExchangeRateDto;
import tech.kayys.sy.currency.core.repository.CurrencyRepository;
import tech.kayys.sy.currency.core.repository.ExchangeRateRepository;
import tech.kayys.sy.currency.exception.BusinessException;

@ApplicationScoped
public class ExchangeRateService {
   private static final Logger log = Logger.getLogger(ExchangeRateService.class);
    
    @Inject
    ExchangeRateRepository exchangeRateRepository;

    @Inject
    CurrencyRepository currencyRepository;

    @Transactional
    public ExchangeRate createExchangeRate(ExchangeRateDto dto) {
        Currency fromCurrency = currencyRepository.findByCode(dto.fromCurrencyCode())
            .orElseThrow(() -> new BusinessException("From currency not found: " + dto.fromCurrencyCode()));
        
        Currency toCurrency = currencyRepository.findByCode(dto.toCurrencyCode())
            .orElseThrow(() -> new BusinessException("To currency not found: " + dto.toCurrencyCode()));

        // Check for overlapping rates
        Optional<ExchangeRate> existingRate = exchangeRateRepository
            .findRateForDate(dto.fromCurrencyCode(), dto.toCurrencyCode(), dto.effectiveDate());
        
        if (existingRate.isPresent()) {
            throw new BusinessException("Exchange rate already exists for this currency pair and date");
        }

        ExchangeRate rate = new ExchangeRate();
        rate.fromCurrency = fromCurrency;
        rate.toCurrency = toCurrency;
        rate.rate = dto.rate();
        rate.effectiveDate = dto.effectiveDate();
        rate.expiryDate = dto.expiryDate();
        rate.active = dto.active();

        exchangeRateRepository.persist(rate);
        log.info("Created exchange rate: {} -> {} : {}"+ dto.fromCurrencyCode()+ dto.toCurrencyCode());
        return rate;
    }

    public Optional<BigDecimal> getExchangeRate(String fromCurrency, String toCurrency, LocalDate date) {
        if (fromCurrency.equals(toCurrency)) {
            return Optional.of(BigDecimal.ONE);
        }

        return exchangeRateRepository.findRateForDate(fromCurrency, toCurrency, date)
            .map(ExchangeRate::getRate);
    }

    public Optional<CurrencyConversionResult> convertCurrency(CurrencyConversionRequest request) {
        LocalDate effectiveDate = request.exchangeDate() != null ? request.exchangeDate() : LocalDate.now();
        
        return getExchangeRate(request.fromCurrency(), request.toCurrency(), effectiveDate)
            .map(rate -> {
                BigDecimal convertedAmount = request.amount().multiply(rate)
                    .setScale(getDecimalPlaces(request.toCurrency()), RoundingMode.HALF_EVEN);
                
                return new CurrencyConversionResult(
                    request.fromCurrency(),
                    request.toCurrency(),
                    request.amount(),
                    convertedAmount,
                    rate,
                    effectiveDate
                );
            });
    }

    private int getDecimalPlaces(String currencyCode) {
        return currencyRepository.findByCode(currencyCode)
            .map(Currency::getDecimalPlaces)
            .orElse(2);
    }

    public List<ExchangeRate> getLatestRates() {
        // Implementation to get latest rates for all active currencies
        return exchangeRateRepository.list(
            "active = true and (expiryDate is null or expiryDate >= current_date) " +
            "and effectiveDate <= current_date " +
            "order by fromCurrency.code, toCurrency.code, effectiveDate desc");
    }
}