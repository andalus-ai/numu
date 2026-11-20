package tech.kayys.numou.applicationplatform.application.navigation;

import java.util.Objects;

/** Safe, declarative navigation item contributed by an enabled application package. */
public record ApplicationNavigationEntry(
        String applicationId,
        String applicationVersion,
        String contributionId,
        String label,
        String route,
        String requiredPermission) {
    public ApplicationNavigationEntry {
        requireText(applicationId, "applicationId", 180);
        requireText(applicationVersion, "applicationVersion", 256);
        requireText(contributionId, "contributionId", 180);
        requireText(label, "label", 80);
        requireText(requiredPermission, "requiredPermission", 160);
        if (route == null || route.length() < 2 || route.length() > 240
                || !route.matches("^/[A-Za-z0-9/_:-]*$"))
            throw new IllegalArgumentException("route must be a local application path");
    }

    public String stableKey() {
        return applicationId + ":" + contributionId;
    }

    private static void requireText(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > max)
            throw new IllegalArgumentException(field + " must contain 1 to " + max + " characters");
    }
}
