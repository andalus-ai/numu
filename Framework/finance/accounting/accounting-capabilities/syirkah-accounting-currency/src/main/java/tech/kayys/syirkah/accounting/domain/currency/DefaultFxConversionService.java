package tech.kayys.syirkah.accounting.domain.currency;

import tech.kayys.syirkah.foundation.domain.valueobject.Currency;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.MonetaryRoundingPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Standard in-memory implementation of {@link FxConversionService}.
 * Supports direct and reciprocal FX conversion with governed rate selection.
 */
public class DefaultFxConversionService implements FxConversionService {

    private final Map<String, ExchangeRate> rates = new ConcurrentHashMap<>();

    public void registerRate(ExchangeRate rate) {
        Objects.requireNonNull(rate, "rate cannot be null");
        rates.put(rateKey(rate.sourceCurrency(), rate.targetCurrency(), rate.rateType()), rate);
    }

    @Override
    public FxConversionResult convert(FxConversionRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        Currency source = request.sourceAmount().currency();
        Currency target = request.targetCurrency();

        if (source.equals(target)) {
            throw new IllegalArgumentException("Cannot perform FX conversion on identical currencies: " + source.code());
        }

        MonetaryRoundingPolicy roundingPolicy = request.roundingPolicy();
        int scale = roundingPolicy.scale();
        RoundingMode mode = roundingPolicy.mode();

        // 1. Check direct rate
        String directKey = rateKey(source, target, request.rateType());
        ExchangeRate directRate = rates.get(directKey);
        if (directRate != null && directRate.status() == ExchangeRateStatus.ACTIVE) {
            BigDecimal convertedAmount = request.sourceAmount().amount()
                    .multiply(directRate.rate())
                    .setScale(scale, mode);
            return new FxConversionResult(
                    request.sourceAmount(),
                    Money.of(convertedAmount, target),
                    directRate.rate(),
                    request.rateType(),
                    request.effectiveAt(),
                    directRate.id()
            );
        }

        // 2. Check reciprocal rate
        String reverseKey = rateKey(target, source, request.rateType());
        ExchangeRate reverseRate = rates.get(reverseKey);
        if (reverseRate != null && reverseRate.status() == ExchangeRateStatus.ACTIVE) {
            // reciprocal factor = 1 / reverseRate.rate()
            BigDecimal calculatedRate = BigDecimal.ONE.divide(reverseRate.rate(), 8, RoundingMode.HALF_UP);
            BigDecimal convertedAmount = request.sourceAmount().amount()
                    .divide(reverseRate.rate(), scale, mode);
            return new FxConversionResult(
                    request.sourceAmount(),
                    Money.of(convertedAmount, target),
                    calculatedRate,
                    request.rateType(),
                    request.effectiveAt(),
                    "RECIPROCAL:" + reverseRate.id()
            );
        }

        throw new IllegalArgumentException(
                "Exchange rate not found for pair " + source.code() + "/" + target.code() + " and type " + request.rateType()
        );
    }

    private static String rateKey(Currency source, Currency target, ExchangeRateType type) {
        return source.code() + "->" + target.code() + ":" + type.name();
    }
}
