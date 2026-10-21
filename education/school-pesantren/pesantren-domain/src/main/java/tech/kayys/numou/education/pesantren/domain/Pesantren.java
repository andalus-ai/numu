package tech.kayys.numou.education.pesantren.domain;

import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.pesantren.domain.event.PesantrenRegistered;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;

/**
 * A pondok pesantren. When it runs a formal school or madrasah, {@code affiliatedSchool}
 * references it by shared-kernel id so the school-management context stays the owner.
 */
public final class Pesantren extends AbstractAggregateRoot<PesantrenId> {

    private final TenantId tenantId;
    private final String name;
    private final String nspp;
    private final PesantrenType type;
    private final SchoolId affiliatedSchool;

    private Pesantren(PesantrenId id, TenantId tenantId, String name, String nspp, PesantrenType type,
                      SchoolId affiliatedSchool) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Pesantren name must not be blank");
        this.name = name.trim();
        if (nspp != null && !nspp.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new IllegalArgumentException("NSPP must contain digits only");
        }
        this.nspp = nspp;
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.affiliatedSchool = affiliatedSchool;
    }

    /** {@code nspp} (Nomor Statistik Pondok Pesantren) and {@code affiliatedSchool} are optional. */
    public static Pesantren register(PesantrenId id, TenantId tenantId, String name, String nspp, PesantrenType type,
                                     SchoolId affiliatedSchool) {
        Pesantren p = new Pesantren(id, tenantId, name, nspp, type, affiliatedSchool);
        p.raise(new PesantrenRegistered(id, tenantId, p.name));
        return p;
    }

    public TenantId getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getNspp() { return nspp; }
    public PesantrenType getType() { return type; }
    public SchoolId getAffiliatedSchool() { return affiliatedSchool; }
}
