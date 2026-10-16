package tech.kayys.syirkah.accounting.domain.islamic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Islamic bond certificate backed by tangible underlying assets.
 */
public record SukukCertificate(
        String certificateId,
        String name,
        IslamicContractType contractType,
        String underlyingAssetReference,
        BigDecimal faceValue,
        BigDecimal profitSharingRatio,
        LocalDate maturityDate
) {
    public SukukCertificate {
        Objects.requireNonNull(certificateId, "certificateId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(contractType, "contractType must not be null");
        Objects.requireNonNull(underlyingAssetReference, "underlyingAssetReference must not be null");
        Objects.requireNonNull(faceValue, "faceValue must not be null");
        Objects.requireNonNull(profitSharingRatio, "profitSharingRatio must not be null");
        Objects.requireNonNull(maturityDate, "maturityDate must not be null");
        if (faceValue.signum() <= 0) throw new IllegalArgumentException("faceValue must be positive");
    }
}
