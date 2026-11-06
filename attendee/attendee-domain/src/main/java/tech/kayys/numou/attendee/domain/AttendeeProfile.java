package tech.kayys.numou.attendee.domain;

import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Contextual attendee profile for an application or participation context.
 *
 * <p>This is deliberately not a person master. The canonical identity is the
 * Ecosystem Party; this aggregate stores only attendee-facing presentation
 * and lifecycle data owned by the consuming tenant. Registration, tickets,
 * attendance, branch assignment, coverage and authorization are separate
 * capabilities and must not be inferred from this profile.
 */
public record AttendeeProfile(
        UUID id,
        TenantId tenantId,
        PartyId partyId,
        String profileKind,
        String displayName,
        String preferredName,
        String preferredLanguage,
        AttendeeProfileStatus status,
        Instant createdAt,
        Instant updatedAt,
        long revision) {

    public AttendeeProfile {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(partyId, "partyId cannot be null");
        profileKind = requireText(profileKind, "profileKind");
        displayName = requireText(displayName, "displayName");
        preferredName = optionalText(preferredName);
        preferredLanguage = optionalText(preferredLanguage);
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
    }

    public static AttendeeProfile create(
            UUID id,
            TenantId tenantId,
            PartyId partyId,
            String profileKind,
            String displayName,
            String preferredName,
            String preferredLanguage,
            Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        return new AttendeeProfile(id, tenantId, partyId, profileKind, displayName,
                preferredName, preferredLanguage, AttendeeProfileStatus.PENDING, now, now, 0);
    }

    public AttendeeProfile activate(Instant now) {
        if (status != AttendeeProfileStatus.PENDING && status != AttendeeProfileStatus.SUSPENDED) {
            throw new IllegalStateException("Only pending or suspended profiles can be activated");
        }
        return changed(AttendeeProfileStatus.ACTIVE, displayName, preferredName, preferredLanguage, now);
    }

    public AttendeeProfile suspend(Instant now) {
        if (status != AttendeeProfileStatus.ACTIVE) {
            throw new IllegalStateException("Only active profiles can be suspended");
        }
        return changed(AttendeeProfileStatus.SUSPENDED, displayName, preferredName, preferredLanguage, now);
    }

    public AttendeeProfile archive(Instant now) {
        if (status == AttendeeProfileStatus.ARCHIVED) {
            throw new IllegalStateException("Profile is already archived");
        }
        return changed(AttendeeProfileStatus.ARCHIVED, displayName, preferredName, preferredLanguage, now);
    }

    public AttendeeProfile updatePresentation(
            String displayName, String preferredName, String preferredLanguage, Instant now) {
        if (status == AttendeeProfileStatus.ARCHIVED) {
            throw new IllegalStateException("Archived profiles cannot be modified");
        }
        return changed(status, displayName, preferredName, preferredLanguage, now);
    }

    private AttendeeProfile changed(
            AttendeeProfileStatus nextStatus,
            String nextDisplayName,
            String nextPreferredName,
            String nextPreferredLanguage,
            Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("now cannot be before updatedAt");
        }
        return new AttendeeProfile(id, tenantId, partyId, profileKind, nextDisplayName,
                nextPreferredName, nextPreferredLanguage, nextStatus, createdAt, now, revision + 1);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
