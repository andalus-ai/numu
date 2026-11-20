package tech.kayys.numou.attendee.application.command;

import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;
import java.util.UUID;

/**
 * Creates a tenant-owned attendee context linked to an existing canonical Party.
 * Tenant and actor identity are deliberately absent; they come from trusted context.
 */
public record CreateAttendeeProfileCommand(
        UUID partyId,
        String profileKind,
        String displayName,
        String preferredName,
        String preferredLanguage) implements Command {

    public CreateAttendeeProfileCommand {
        Objects.requireNonNull(partyId, "partyId cannot be null");
        if (profileKind == null || profileKind.isBlank()) {
            throw new IllegalArgumentException("profileKind is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
        profileKind = profileKind.trim();
        displayName = displayName.trim();
        preferredName = normalize(preferredName);
        preferredLanguage = normalize(preferredLanguage);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
