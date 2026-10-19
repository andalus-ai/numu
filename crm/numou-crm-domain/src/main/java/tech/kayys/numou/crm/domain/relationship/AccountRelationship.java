package tech.kayys.numou.crm.domain.relationship;

import tech.kayys.numou.crm.domain.identifier.AccountId;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.crm.domain.event.AccountRelationshipCreated;
import tech.kayys.numou.crm.domain.event.AccountRelationshipTerminated;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a typed relationship between two accounts.
 */
public final class AccountRelationship extends AbstractAggregateRoot<AccountRelationshipId> {

    private final AccountId sourceAccountId;
    private final AccountId targetAccountId;
    private final AccountRelationshipType type;
    private Instant effectiveAt;
    private Instant terminatedAt;
    private String description;

    private AccountRelationship(AccountRelationshipId id, AccountId sourceAccountId, AccountId targetAccountId,
                                AccountRelationshipType type) {
        super(id);
        this.sourceAccountId = Objects.requireNonNull(sourceAccountId, "sourceAccountId cannot be null");
        this.targetAccountId = Objects.requireNonNull(targetAccountId, "targetAccountId cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.effectiveAt = Instant.now();
    }

    public static AccountRelationship create(AccountRelationshipId id,
                                             AccountId sourceAccountId,
                                             AccountId targetAccountId,
                                             AccountRelationshipType type) {
        AccountRelationship relationship = new AccountRelationship(id, sourceAccountId, targetAccountId, type);
        relationship.raise(AccountRelationshipCreated.of(
                relationship.id().value(),
                sourceAccountId.value(),
                targetAccountId.value(),
                type.name(),
                Instant.now().toEpochMilli()
        ));
        return relationship;
    }

    public static AccountRelationship restore(AccountRelationshipId id,
                                              AccountId sourceAccountId,
                                              AccountId targetAccountId,
                                              AccountRelationshipType type,
                                              Instant effectiveAt,
                                              Instant terminatedAt,
                                              String description) {
        AccountRelationship relationship = new AccountRelationship(id, sourceAccountId, targetAccountId, type);
        relationship.effectiveAt = Objects.requireNonNull(effectiveAt, "effectiveAt cannot be null");
        relationship.terminatedAt = terminatedAt;
        relationship.description = description;
        return relationship;
    }

    public AccountId sourceAccountId() {
        return sourceAccountId;
    }

    public AccountId targetAccountId() {
        return targetAccountId;
    }

    public AccountRelationshipType type() {
        return type;
    }

    public Instant effectiveAt() {
        return effectiveAt;
    }

    public Instant terminatedAt() {
        return terminatedAt;
    }

    public String description() {
        return description;
    }

    public void terminate(String description) {
        if (terminatedAt != null) {
            throw new IllegalStateException("Relationship already terminated");
        }
        this.terminatedAt = Instant.now();
        this.description = Objects.requireNonNull(description, "description cannot be null");
        touch();
        raise(AccountRelationshipTerminated.of(
                id().value(),
                sourceAccountId.value(),
                targetAccountId.value(),
                type.name(),
                Instant.now().toEpochMilli()
        ));
    }

    public boolean isActive() {
        return terminatedAt == null;
    }

    private void touch() {
        setUpdatedAt(Instant.now());
        incrementVersion();
    }
}