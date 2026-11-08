package tech.kayys.numou.attendee.application.query;

import tech.kayys.numou.foundation.application.query.Query;

import java.util.Objects;
import java.util.UUID;

/** Tenant-scoped lookup by attendee profile identifier. */
public record GetAttendeeProfileQuery(UUID profileId) implements Query {
    public GetAttendeeProfileQuery {
        Objects.requireNonNull(profileId, "profileId cannot be null");
    }
}
