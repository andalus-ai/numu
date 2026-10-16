package tech.kayys.numou.crm.domain.account;

import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.crm.domain.identifier.AccountId;
import tech.kayys.numou.crm.domain.valueobject.AccountStatus;
import tech.kayys.numou.crm.domain.valueobject.PartyRole;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * CRM's commercial context for an ecosystem participant.
 */
public final class Account extends AbstractAggregateRoot<AccountId> {

    private final ParticipantId participantId;
    private AccountId parentAccountId;
    private String name;
    private AccountStatus status;
    private Set<PartyRole> roles;

    private Account(AccountId id, ParticipantId participantId, String name) {
        super(Objects.requireNonNull(id, "id cannot be null"));
        this.participantId = Objects.requireNonNull(participantId, "participantId cannot be null");
        this.name = requireName(name);
        this.status = AccountStatus.ACTIVE;
        this.roles = new HashSet<>();
    }

    public static Account create(AccountId id, ParticipantId participantId, String name) {
        return new Account(id, participantId, name);
    }

    public static Account restore(AccountId id, ParticipantId participantId, String name,
                                  AccountStatus status, AccountId parentAccountId) {
        return restore(id, participantId, name, status, parentAccountId, Collections.emptySet());
    }

    public static Account restore(AccountId id, ParticipantId participantId, String name,
                                  AccountStatus status, AccountId parentAccountId, Set<PartyRole> roles) {
        Account account = new Account(id, participantId, name);
        account.status = Objects.requireNonNull(status, "status cannot be null");
        account.requireNotSelf(parentAccountId);
        account.parentAccountId = parentAccountId;
        account.roles = Objects.requireNonNull(roles, "roles cannot be null");
        return account;
    }

    public ParticipantId participantId() {
        return participantId;
    }

    public AccountId parentAccountId() {
        return parentAccountId;
    }

    public String name() {
        return name;
    }

    public AccountStatus status() {
        return status;
    }

    public void rename(String name) {
        this.name = requireName(name);
        touch();
    }

    public void assignParent(AccountId parentAccountId) {
        AccountId requiredParent = Objects.requireNonNull(parentAccountId, "parentAccountId cannot be null");
        requireNotSelf(requiredParent);
        this.parentAccountId = requiredParent;
        touch();
    }

    public void removeParent() {
        this.parentAccountId = null;
        touch();
    }

    public void deactivate() {
        if (status == AccountStatus.INACTIVE) {
            return;
        }
        status = AccountStatus.INACTIVE;
        touch();
    }

    public void activate() {
        if (status == AccountStatus.ACTIVE) {
            return;
        }
        status = AccountStatus.ACTIVE;
        touch();
    }

    public Set<PartyRole> partyRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public void addPartyRole(PartyRole role) {
        this.roles.add(Objects.requireNonNull(role, "role cannot be null"));
        touch();
    }

    public void removePartyRole(PartyRole role) {
        this.roles.remove(Objects.requireNonNull(role, "role cannot be null"));
        touch();
    }

    public boolean hasPartyRole(PartyRole role) {
        return this.roles.contains(Objects.requireNonNull(role, "role cannot be null"));
    }


    private void touch() {
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    private static String requireName(String value) {
        Objects.requireNonNull(value, "name cannot be null");
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        return normalized;
    }

    private void requireNotSelf(AccountId parentAccountId) {
        if (id().equals(parentAccountId)) {
            throw new IllegalArgumentException("Account cannot be its own parent");
        }
    }
}
