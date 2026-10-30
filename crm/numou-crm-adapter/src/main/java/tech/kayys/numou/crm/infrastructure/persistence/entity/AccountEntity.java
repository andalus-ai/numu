package tech.kayys.numou.crm.infrastructure.persistence.entity;

import tech.kayys.numou.foundation.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

import tech.kayys.numou.crm.domain.valueobject.AccountStatus;

@Entity
@Table(name = "crm_accounts", indexes = {
        @Index(name = "idx_crm_account_participant", columnList = "participant_id"),
        @Index(name = "idx_crm_account_parent", columnList = "parent_account_id"),
        @Index(name = "idx_crm_account_tenant", columnList = "tenant_id"),
        @Index(name = "idx_crm_account_tenant_parent", columnList = "tenant_id, parent_account_id")
})
public class AccountEntity extends BaseEntity {

    @Column(name = "tenant_id")
    public UUID tenantId;

    @Column(name = "participant_id", nullable = false, updatable = false)
    public UUID participantId;

    @Column(name = "parent_account_id")
    public UUID parentAccountId;

    @Column(name = "name", nullable = false, length = 200)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public AccountStatus status;

    @Column(name = "roles")
    public String roles; // comma-separated list of role names
}
