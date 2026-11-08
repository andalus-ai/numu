package tech.kayys.numou.attendee.application.command;

import tech.kayys.numou.attendee.domain.AttendeeProfileStatus;

import java.util.Objects;
import java.util.UUID;

public record CreateAttendeeProfileResult(
        UUID profileId,
        AttendeeProfileStatus status,
        long revision) {
    public CreateAttendeeProfileResult {
        Objects.requireNonNull(profileId, "profileId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        if (revision < 0) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
    }
}
