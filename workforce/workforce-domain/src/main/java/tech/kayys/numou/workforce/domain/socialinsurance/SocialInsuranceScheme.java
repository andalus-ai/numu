package tech.kayys.numou.workforce.domain.socialinsurance;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;

/**
 * SocialInsuranceScheme defines an insurance or social security scheme (e.g. BPJS Kesehatan, BPJS Ketenagakerjaan, CPF, EPF).
 *
 * <p>Schemes are configuration/data rather than hard-coded domain enums, ensuring international applicability.
 */
public final class SocialInsuranceScheme extends AbstractAggregateRoot<SocialInsuranceSchemeId> {

    private final TenantId tenantId;
    private final String code;
    private String name;
    private SocialInsuranceSchemeStatus status;

    private SocialInsuranceScheme(
            SocialInsuranceSchemeId id,
            TenantId tenantId,
            String code,
            String name
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        if (code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.status = SocialInsuranceSchemeStatus.ACTIVE;
    }

    public static SocialInsuranceScheme create(
            SocialInsuranceSchemeId id,
            TenantId tenantId,
            String code,
            String name
    ) {
        return new SocialInsuranceScheme(id, tenantId, code, name);
    }

    public void rename(String newName) {
        Objects.requireNonNull(newName, "name must not be null");
        if (newName.isBlank()) throw new IllegalArgumentException("name must not be blank");
        this.name = newName;
    }

    public void activate() {
        this.status = SocialInsuranceSchemeStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = SocialInsuranceSchemeStatus.INACTIVE;
    }

    public TenantId tenantId() { return tenantId; }
    public String code() { return code; }
    public String name() { return name; }
    public SocialInsuranceSchemeStatus status() { return status; }
    public boolean isActive() { return status == SocialInsuranceSchemeStatus.ACTIVE; }
}
