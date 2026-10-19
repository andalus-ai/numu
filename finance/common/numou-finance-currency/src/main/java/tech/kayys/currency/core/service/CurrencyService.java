package tech.kayys.sy.currency.core.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.sy.currency.core.domain.Currency;
import tech.kayys.sy.currency.core.domain.ExchangeRate;
import tech.kayys.sy.currency.core.dto.CurrencyConversionRequest;
import tech.kayys.sy.currency.core.dto.CurrencyConversionResult;
import tech.kayys.sy.currency.core.dto.CurrencyCreateDto;
import tech.kayys.sy.currency.core.dto.CurrencyInfo;
import tech.kayys.sy.currency.core.dto.ExchangeRateDto;
import tech.kayys.sy.currency.core.repository.CurrencyRepository;
import tech.kayys.sy.currency.core.repository.ExchangeRateRepository;
import tech.kayys.sy.currency.exception.BusinessException;

@ApplicationScoped
public class CurrencyService {
    private static final Logger log = Logger.getLogger(CurrencyService.class);

    @Inject
    CurrencyRepository currencyRepository;

    @Inject
    CurrencyValidationService validationService;

    public List<Currency> listAll() {
        return currencyRepository.findAllActive();
    }

    public Optional<Currency> findByCode(String code) {
        return currencyRepository.findByCode(code.toUpperCase());
    }

    @Transactional
    public Currency create(Currency currency) {
        if (currencyRepository.existsByCode(currency.code)) {
            throw new BusinessException("Currency with code " + currency.code + " already exists");
        }

        currency.code = currency.code.toUpperCase();
        currencyRepository.persist(currency);
        log.info("Created currency: {}" + currency.code);
        return currency;
    }

    @Transactional
    public Currency update(String code, Currency updatedCurrency) {
        Currency existing = findByCode(code)
                .orElseThrow(() -> new BusinessException("Currency not found: " + code));

        existing.name = updatedCurrency.name;
        existing.symbol = updatedCurrency.symbol;
        existing.decimalPlaces = updatedCurrency.decimalPlaces;
        existing.active = updatedCurrency.active;

        log.info("Updated currency: {}" + code);
        return existing;
    }

    @Transactional
    public void deactivate(String code) {
        Currency currency = findByCode(code)
                .orElseThrow(() -> new BusinessException("Currency not found: " + code));

        currency.active = false;
        log.infof("Deactivated currency: %s", code);
    }

    @Transactional
    public Currency create(CurrencyCreateDto dto) {
        validationService.validateCurrencyCreation(dto);

        if (currencyRepository.existsByCode(dto.code)) {
            throw new BusinessException("Currency with code " + dto.code + " already exists");
        }

        Currency currency = new Currency();
        currency.code = dto.code.toUpperCase();
        currency.name = dto.name;
        currency.symbol = dto.symbol;
        currency.decimalPlaces = dto.decimalPlaces;
        currency.active = dto.active;

        // For ISO currencies, we can enrich data from java.util.Currency
        validationService.getJavaCurrency(dto.code).ifPresent(jdkCurrency -> {
            log.infof("Creating ISO currency: %s with %d decimal places",
                    dto.code, jdkCurrency.getDefaultFractionDigits());
        });

        currencyRepository.persist(currency);
        log.infof("Created currency: %s", currency.code);
        return currency;
    }

    public CurrencyInfo getCurrencyInfo(String code) {
        Currency currency = findByCode(code)
                .orElseThrow(() -> new BusinessException("Currency not found: " + code));

        CurrencyInfo info = new CurrencyInfo();
        info.setCode(currency.code);
        info.setName(currency.name);
        info.setSymbol(currency.symbol);
        info.setDecimalPlaces(currency.decimalPlaces);
        info.setCustom(!validationService.isValidIsoCurrency(code));

        // Add JDK currency info if available
        validationService.getJavaCurrency(code).ifPresent(jdkCurrency -> {
            info.setIsoNumericCode(jdkCurrency.getNumericCode());
            info.setIsoDisplayName(jdkCurrency.getDisplayName(Locale.ENGLISH));
        });

        return info;
    }

    // ... other methods
}
