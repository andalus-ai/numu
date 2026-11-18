package tech.kayys.numou.applicationplatform.domain.installation;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SemVer 2.0.0 package version validated at the application-platform boundary. */
public record PackageVersion(String value) {
    private static final Pattern FORMAT = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z.-]+))?(?:\\+([0-9A-Za-z.-]+))?$");

    public PackageVersion {
        Objects.requireNonNull(value, "package version is required");
        if (value.length() > 256) throw new IllegalArgumentException("Package version must not exceed 256 characters");
        Matcher matcher = FORMAT.matcher(value);
        if (!matcher.matches()) throw new IllegalArgumentException("Package version must be SemVer 2.0.0");
        validateIdentifiers(matcher.group(4), true);
        validateIdentifiers(matcher.group(5), false);
    }

    private static void validateIdentifiers(String identifiers, boolean prerelease) {
        if (identifiers == null) return;
        for (String identifier : identifiers.split("\\.", -1)) {
            if (identifier.isEmpty()) throw new IllegalArgumentException("SemVer identifiers must not be empty");
            if (prerelease && identifier.matches("\\d+") && identifier.length() > 1
                    && identifier.startsWith("0"))
                throw new IllegalArgumentException("Numeric prerelease identifiers must not contain leading zeroes");
        }
    }
}
