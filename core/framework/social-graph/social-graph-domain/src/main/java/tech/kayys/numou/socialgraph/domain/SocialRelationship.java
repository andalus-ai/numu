package tech.kayys.numou.socialgraph.domain;

import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A tenant-scoped, directed relationship between canonical Ecosystem Parties.
 *
 * <p>This aggregate deliberately does not represent bilateral connection
 * requests. Those retain their richer recipient-approval lifecycle in
 * {@link SocialConnection}. FOLLOW, BLOCK and MUTE are directed edges; a
 * policy that needs direction-independent block behavior must check both
 * orientations in its query contract.</p>
 */
public final class SocialRelationship {
    private static final int MAX_METADATA_ENTRIES = 32;
    private static final int MAX_METADATA_KEY_LENGTH = 64;
    private static final int MAX_METADATA_VALUE_LENGTH = 512;
    private static final int MAX_PROVENANCE_LENGTH = 128;

    private final SocialRelationshipId id;
    private final TenantId tenantId;
    private final SocialRelationshipType type;
    private final PartyId sourcePartyId;
    private final PartyId targetPartyId;
    private final SocialRelationshipStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant validFrom;
    private final Instant validUntil;
    private final Instant removedAt;
    private final String provenance;
    private final Map<String, String> metadata;
    private final int version;

    private SocialRelationship(
            SocialRelationshipId id,
            TenantId tenantId,
            SocialRelationshipType type,
            PartyId sourcePartyId,
            PartyId targetPartyId,
            SocialRelationshipStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant validFrom,
            Instant validUntil,
            Instant removedAt,
            String provenance,
            Map<String, String> metadata,
            int version) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.sourcePartyId = Objects.requireNonNull(sourcePartyId, "sourcePartyId cannot be null");
        this.targetPartyId = Objects.requireNonNull(targetPartyId, "targetPartyId cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
        this.validFrom = Objects.requireNonNull(validFrom, "validFrom cannot be null");
        this.validUntil = validUntil;
        this.removedAt = removedAt;
        this.provenance = requireProvenance(provenance);
        this.metadata = immutableMetadata(metadata);
        if (sourcePartyId.equals(targetPartyId)) {
            throw new IllegalArgumentException("A Party cannot have a relationship with itself");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
        this.version = version;
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt precedes createdAt");
        }
        if (validFrom.isBefore(createdAt)) {
            throw new IllegalArgumentException("validFrom precedes createdAt");
        }
        if (validUntil != null && !validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException("validUntil must be after validFrom");
        }
        if (status == SocialRelationshipStatus.ACTIVE && removedAt != null) {
            throw new IllegalArgumentException("Active relationship cannot have removedAt");
        }
        if (status == SocialRelationshipStatus.REMOVED
                && (removedAt == null || removedAt.isBefore(updatedAt))) {
            throw new IllegalArgumentException("Removed relationship requires removedAt at or after updatedAt");
        }
    }

    public static SocialRelationship create(
            SocialRelationshipId id,
            TenantId tenantId,
            SocialRelationshipType type,
            PartyId source,
            PartyId target,
            Instant now,
            Instant validFrom,
            Instant validUntil,
            String provenance,
            Map<String, String> metadata) {
        return new SocialRelationship(id, tenantId, type, source, target,
                SocialRelationshipStatus.ACTIVE, now, now, validFrom, validUntil,
                null, provenance, metadata, 0);
    }

    public static SocialRelationship rehydrate(
            SocialRelationshipId id,
            TenantId tenantId,
            SocialRelationshipType type,
            PartyId source,
            PartyId target,
            SocialRelationshipStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant validFrom,
            Instant validUntil,
            Instant removedAt,
            String provenance,
            Map<String, String> metadata,
            int version) {
        return new SocialRelationship(id, tenantId, type, source, target, status,
                createdAt, updatedAt, validFrom, validUntil, removedAt,
                provenance, metadata, version);
    }

    /** Only the Party that created a directed edge may remove it. */
    public SocialRelationship remove(PartyId actor, Instant now) {
        Objects.requireNonNull(actor, "actor cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        if (!sourcePartyId.equals(actor)) {
            throw new IllegalArgumentException("Only the source Party may remove this relationship");
        }
        if (status != SocialRelationshipStatus.ACTIVE) {
            throw new IllegalStateException("Relationship is already removed");
        }
        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Transition time cannot move backwards");
        }
        return new SocialRelationship(id, tenantId, type, sourcePartyId, targetPartyId,
                SocialRelationshipStatus.REMOVED, createdAt, now, validFrom, validUntil,
                now, provenance, metadata, version + 1);
    }

    /** Whether this edge is active at the supplied instant, including scheduled validity. */
    public boolean isEffectiveAt(Instant instant) {
        Objects.requireNonNull(instant, "instant cannot be null");
        return status == SocialRelationshipStatus.ACTIVE
                && !instant.isBefore(validFrom)
                && (validUntil == null || instant.isBefore(validUntil));
    }

    private static String requireProvenance(String provenance) {
        if (provenance == null || provenance.isBlank() || provenance.length() > MAX_PROVENANCE_LENGTH) {
            throw new IllegalArgumentException("provenance must be non-blank and at most 128 characters");
        }
        return provenance;
    }

    private static Map<String, String> immutableMetadata(Map<String, String> metadata) {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        if (metadata.size() > MAX_METADATA_ENTRIES) {
            throw new IllegalArgumentException("metadata cannot contain more than 32 entries");
        }
        Map<String, String> copy = new TreeMap<>();
        metadata.forEach((key, value) -> {
            if (key == null || key.isBlank() || key.length() > MAX_METADATA_KEY_LENGTH) {
                throw new IllegalArgumentException("metadata keys must be non-blank and at most 64 characters");
            }
            if (value == null || value.length() > MAX_METADATA_VALUE_LENGTH) {
                throw new IllegalArgumentException("metadata values must be non-null and at most 512 characters");
            }
            copy.put(key, value);
        });
        return Map.copyOf(copy);
    }

    public SocialRelationshipId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public SocialRelationshipType type() { return type; }
    public PartyId sourcePartyId() { return sourcePartyId; }
    public PartyId targetPartyId() { return targetPartyId; }
    public SocialRelationshipStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant validFrom() { return validFrom; }
    public Instant validUntil() { return validUntil; }
    public Instant removedAt() { return removedAt; }
    public String provenance() { return provenance; }
    public Map<String, String> metadata() { return metadata; }
    public int version() { return version; }
}
