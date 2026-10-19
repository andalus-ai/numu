package tech.kayys.numou.crm.domain.referral;

import tech.kayys.numou.crm.domain.identifier.AccountId;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.crm.domain.event.referral.ReferralAccepted;
import tech.kayys.numou.crm.domain.event.referral.ReferralCancelled;
import tech.kayys.numou.crm.domain.event.referral.ReferralConverted;
import tech.kayys.numou.crm.domain.event.referral.ReferralCreated;
import tech.kayys.numou.crm.domain.event.referral.ReferralRejected;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a referral (business introduction) from one account to a lead or opportunity.
 */
public class Referral extends AbstractAggregateRoot<ReferralId> {

    private final ReferralId id;
    private final AccountId referrerAccountId;
    private ReferralTarget target;
    private ReferralType type;
    private ReferralStatus status;
    private String code;
    private String description;
    private Instant referredAt;
    private Instant acceptedAt;
    private Instant convertedAt;
    private UUID targetLeadId;
    private UUID targetOpportunityId;
    private String externalReference;

    private Referral(ReferralId id, AccountId referrerAccountId) {
        super(id);
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.referrerAccountId = Objects.requireNonNull(referrerAccountId, "referrerAccountId cannot be null");
        this.status = ReferralStatus.PENDING;
    }

    public static Referral create(ReferralId id,
                                  AccountId referrerAccountId,
                                  ReferralTarget target,
                                  ReferralType type,
                                  String code,
                                  String description) {
        Referral referral = new Referral(id, referrerAccountId);
        referral.target = Objects.requireNonNull(target, "target cannot be null");
        referral.type = Objects.requireNonNull(type, "type cannot be null");
        referral.code = Objects.requireNonNull(code, "code cannot be null");
        referral.description = Objects.requireNonNull(description, "description cannot be null");
        referral.referredAt = Instant.now();
        referral.raise(ReferralCreated.of(
                referral.id.value(),
                referral.referrerAccountId.value(),
                referral.target.name(),
                referral.type.name(),
                referral.code,
                Instant.now().toEpochMilli()
        ));
        return referral;
    }

    public static Referral restore(ReferralId id,
                                   AccountId referrerAccountId,
                                   ReferralTarget target,
                                   ReferralType type,
                                   ReferralStatus status,
                                   String code,
                                   String description,
                                   Instant referredAt,
                                   Instant acceptedAt,
                                   Instant convertedAt,
                                   UUID targetLeadId,
                                   UUID targetOpportunityId,
                                   String externalReference) {
        Referral referral = new Referral(id, referrerAccountId);
        referral.target = Objects.requireNonNull(target, "target cannot be null");
        referral.type = Objects.requireNonNull(type, "type cannot be null");
        referral.status = Objects.requireNonNull(status, "status cannot be null");
        referral.code = Objects.requireNonNull(code, "code cannot be null");
        referral.description = Objects.requireNonNull(description, "description cannot be null");
        referral.referredAt = Objects.requireNonNull(referredAt, "referredAt cannot be null");
        referral.acceptedAt = acceptedAt;
        referral.convertedAt = convertedAt;
        referral.targetLeadId = targetLeadId;
        referral.targetOpportunityId = targetOpportunityId;
        referral.externalReference = externalReference;
        return referral;
    }

    public ReferralId id() {
        return id;
    }

    public AccountId referrerAccountId() {
        return referrerAccountId;
    }

    public ReferralTarget target() {
        return target;
    }

    public ReferralType type() {
        return type;
    }

    public ReferralStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public Instant referredAt() {
        return referredAt;
    }

    public Instant acceptedAt() {
        return acceptedAt;
    }

    public Instant convertedAt() {
        return convertedAt;
    }

    public UUID targetLeadId() {
        return targetLeadId;
    }

    public UUID targetOpportunityId() {
        return targetOpportunityId;
    }

    public String externalReference() {
        return externalReference;
    }

    public void accept() {
        if (status != ReferralStatus.PENDING) {
            throw new IllegalStateException("Only pending referral can be accepted");
        }
        this.status = ReferralStatus.ACCEPTED;
        this.acceptedAt = Instant.now();
        touch();
        raise(ReferralAccepted.of(
                id.value(),
                referrerAccountId.value(),
                target.name(),
                type.name(),
                code,
                Instant.now().toEpochMilli()
        ));
    }

    public void reject() {
        if (status != ReferralStatus.PENDING) {
            throw new IllegalStateException("Only pending referral can be rejected");
        }
        this.status = ReferralStatus.REJECTED;
        touch();
        raise(ReferralRejected.of(
                id.value(),
                referrerAccountId.value(),
                target.name(),
                type.name(),
                code,
                Instant.now().toEpochMilli()
        ));
    }

    public void convert(UUID leadId, UUID opportunityId) {
        if (status != ReferralStatus.ACCEPTED) {
            throw new IllegalStateException("Only accepted referral can be converted");
        }
        this.status = ReferralStatus.CONVERTED;
        this.convertedAt = Instant.now();
        if (target == ReferralTarget.LEAD) {
            this.targetLeadId = Objects.requireNonNull(leadId, "leadId cannot be null");
        } else if (target == ReferralTarget.OPPORTUNITY) {
            this.targetOpportunityId = Objects.requireNonNull(opportunityId, "opportunityId cannot be null");
        } else {
            this.targetLeadId = leadId;
            this.targetOpportunityId = opportunityId;
        }
        touch();
        raise(ReferralConverted.of(
                id.value(),
                referrerAccountId.value(),
                target.name(),
                type.name(),
                code,
                leadId,
                opportunityId,
                Instant.now().toEpochMilli()
        ));
    }

    public void cancel() {
        if (status == ReferralStatus.CONVERTED || status == ReferralStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel converted or already cancelled referral");
        }
        this.status = ReferralStatus.CANCELLED;
        touch();
        raise(ReferralCancelled.of(
                id.value(),
                referrerAccountId.value(),
                target.name(),
                type.name(),
                code,
                Instant.now().toEpochMilli()
        ));
    }

    private void touch() {
        setUpdatedAt(Instant.now());
        incrementVersion();
    }
}