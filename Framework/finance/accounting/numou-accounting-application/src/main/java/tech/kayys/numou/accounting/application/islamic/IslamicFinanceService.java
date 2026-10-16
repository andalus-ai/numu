package tech.kayys.syirkah.accounting.application.islamic;

import tech.kayys.syirkah.accounting.domain.islamic.IslamicContractType;
import tech.kayys.syirkah.accounting.domain.islamic.SukukCertificate;
import tech.kayys.syirkah.accounting.domain.islamic.ZakatCalculation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application service for AAOIFI-compliant Islamic banking and finance transactions.
 */
public final class IslamicFinanceService {

    private final Map<String, SukukCertificate> sukukRegistry = new ConcurrentHashMap<>();
    private final ZakatEngine zakatEngine;

    public IslamicFinanceService(ZakatEngine zakatEngine) {
        this.zakatEngine = Objects.requireNonNull(zakatEngine, "zakatEngine must not be null");
    }

    public SukukCertificate registerSukuk(
            String certificateId,
            String name,
            IslamicContractType contractType,
            String underlyingAssetReference,
            BigDecimal faceValue,
            BigDecimal profitSharingRatio,
            LocalDate maturityDate
    ) {
        SukukCertificate cert = new SukukCertificate(
                certificateId, name, contractType, underlyingAssetReference, faceValue, profitSharingRatio, maturityDate
        );
        sukukRegistry.put(cert.certificateId(), cert);
        return cert;
    }

    public ZakatCalculation assessCorporateZakat(
            BigDecimal currentAssets,
            BigDecimal currentLiabilities,
            BigDecimal goldPricePerGram,
            boolean isSolarYear
    ) {
        return zakatEngine.calculateZakat(currentAssets, currentLiabilities, goldPricePerGram, isSolarYear);
    }

    public Optional<SukukCertificate> findSukuk(String certificateId) {
        return Optional.ofNullable(sukukRegistry.get(certificateId));
    }
}
