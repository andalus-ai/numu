package tech.kayys.numou.applicationplatform.domain.installation;

import java.util.Objects;
import java.util.regex.Pattern;

/** A SemVer 2.0.0 package version. Full precedence/range handling lives in the manifest contract. */
public record PackageVersion(String value) {
    private static final Pattern FORMAT = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?$");
    public PackageVersion {
        Objects.requireNonNull(value, "package version is required");
        if (!FORMAT.matcher(value).matches() || value.contains("..") || value.endsWith("-") || value.endsWith("+"))
            throw new IllegalArgumentException("Package version must be SemVer 2.0.0");
    }
}
