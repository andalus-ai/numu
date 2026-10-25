package tech.kayys.numou.documents.storage.config;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Runtime configuration model. Bind environment variables or application
 * configuration into this type in the host application's composition layer.
 * EXTERNAL defaults to a local RustFS endpoint for development; production
 * deployments must supply an HTTPS endpoint explicitly.
 */
public record DocumentStorageSettings(
        StorageMode mode,
        String provider,
        Path localRoot,
        URI endpoint,
        String region,
        String bucket,
        boolean pathStyleAccess,
        String accessKey,
        String secretKey,
        boolean production
) {
    public DocumentStorageSettings {
        Objects.requireNonNull(mode, "mode");
        provider = provider == null || provider.isBlank()
                ? (mode == StorageMode.EXTERNAL ? "rustfs" : (mode == StorageMode.LOCAL ? "local" : "s3"))
                : provider.trim().toLowerCase(Locale.ROOT);
        localRoot = localRoot == null ? Path.of("./data/documents") : localRoot;
        region = region == null || region.isBlank() ? "us-east-1" : region.trim();
        if (mode == StorageMode.EXTERNAL && endpoint == null) {
            endpoint = URI.create("http://localhost:9000");
        }
        if (mode != StorageMode.LOCAL && (bucket == null || bucket.isBlank())) {
            throw new IllegalArgumentException("A bucket is required for cloud and external storage");
        }
        if (endpoint != null) {
            String scheme = endpoint.getScheme();
            if (endpoint.getHost() == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Storage endpoint must be an absolute HTTP(S) URI with a host");
            }
            if (endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
                throw new IllegalArgumentException("Storage endpoint must not include user-info or a fragment");
            }
        }
        if (mode == StorageMode.CLOUD && !"s3".equals(provider)) {
            throw new IllegalArgumentException("Unsupported cloud storage provider: " + provider);
        }
        if (mode == StorageMode.EXTERNAL && !"rustfs".equals(provider) && !"s3".equals(provider)) {
            throw new IllegalArgumentException("External storage provider must be rustfs or s3");
        }
        if (mode == StorageMode.LOCAL && !"local".equals(provider)) {
            throw new IllegalArgumentException("LOCAL mode requires provider=local");
        }
        if ((accessKey == null) != (secretKey == null)) {
            throw new IllegalArgumentException("Access key and secret key must be supplied together");
        }
        if (production && endpoint != null && !"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new IllegalArgumentException("Production external storage requires an HTTPS endpoint");
        }
    }

    public static DocumentStorageSettings local(Path root) {
        return new DocumentStorageSettings(StorageMode.LOCAL, "local", root, null,
                null, null, false, null, null, false);
    }

    public static DocumentStorageSettings externalRustFs(URI endpoint, String bucket,
                                                           String accessKey, String secretKey,
                                                           boolean production) {
        return new DocumentStorageSettings(StorageMode.EXTERNAL, "rustfs", null, endpoint,
                "us-east-1", bucket, true, accessKey, secretKey, production);
    }
}
