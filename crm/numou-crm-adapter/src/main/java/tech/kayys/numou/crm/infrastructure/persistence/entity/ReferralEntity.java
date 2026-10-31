package tech.kayys.numou.crm.infrastructure.persistence.entity;

import tech.kayys.numou.crm.domain.referral.ReferralStatus;
import tech.kayys.numou.crm.domain.referral.ReferralTarget;
import tech.kayys.numou.crm.domain.referral.ReferralType;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Referral entity for persistence. Tenant ownership is nullable only to allow
 * controlled reconciliation of legacy rows; application queries fail closed.
 */
@Entity
@Table(name = "crm_referrals", indexes = {
    @Index(name = "idx_referral_tenant", columnList = "tenant_id"),
    @Index(name = "idx_referral_tenant_referrer", columnList = "tenant_id, referrer_account_id"),
    @Index(name = "idx_referral_tenant_status", columnList = "tenant_id, status"),
    @Index(name = "idx_referral_tenant_code", columnList = "tenant_id, code")
})
public class ReferralEntity extends BaseEntity {

    @Column(name = "tenant_id")
    public UUID tenantId;

    @Column(name = "referrer_account_id", nullable = false)
    public UUID referrerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target", nullable = false, length = 20)
    public ReferralTarget target;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    public ReferralType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public ReferralStatus status;

    @Column(name = "code", nullable = false, length = 100)
    public String code;

    @Column(name = "description", length = 2000)
    public String description;

    @Column(name = "referred_at")
    public Instant referredAt;

    @Column(name = "accepted_at")
    public Instant acceptedAt;

    @Column(name = "converted_at")
    public Instant convertedAt;

    @Column(name = "target_lead_id")
    public UUID targetLeadId;

    @Column(name = "target_opportunity_id")
    public UUID targetOpportunityId;

    @Column(name = "external_reference", length = 200)
    public String externalReference;
}
