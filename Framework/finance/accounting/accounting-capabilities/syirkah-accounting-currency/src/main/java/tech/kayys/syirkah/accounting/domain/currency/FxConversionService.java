package tech.kayys.syirkah.accounting.domain.currency;

/**
 * Service port for executing currency conversions.
 */
public interface FxConversionService {

    /**
     * Converts a monetary amount to a target currency using governed exchange rates.
     */
    FxConversionResult convert(FxConversionRequest request);
}
