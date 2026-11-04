package tech.kayys.numou.applicationplatform.domain.installation;

import java.util.Objects;
import java.util.regex.Pattern;

public record ApplicationId(String value) {
    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$");
    public ApplicationId {
        Objects.requireNonNull(value, "application ID is required");
        value = value.trim();
        if (value.length() < 5 || value.length() > 180 || !FORMAT.matcher(value).matches())
            throw new IllegalArgumentException("Application ID must be a reverse-DNS identifier");
    }
}
