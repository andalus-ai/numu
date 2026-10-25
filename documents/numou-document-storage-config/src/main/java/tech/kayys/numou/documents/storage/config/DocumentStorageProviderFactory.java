package tech.kayys.numou.documents.storage.config;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import tech.kayys.numou.documents.storage.local.LocalFileDocumentStorage;
import tech.kayys.numou.documents.storage.s3.S3CompatibleDocumentStorage;
import tech.kayys.numou.documents.storage.spi.DocumentStorage;

import java.io.IOException;
import java.net.URI;

/** Creates the configured adapter; invalid settings fail closed without fallback. */
public final class DocumentStorageProviderFactory {
    private DocumentStorageProviderFactory() { }

    public static DocumentStorage create(DocumentStorageSettings settings) throws IOException {
        if (settings == null) throw new IllegalArgumentException("settings are required");
        return switch (settings.mode()) {
            case LOCAL -> new LocalFileDocumentStorage(settings.localRoot());
            case CLOUD, EXTERNAL -> createS3Compatible(settings);
        };
    }

    private static DocumentStorage createS3Compatible(DocumentStorageSettings settings) {
        S3Client.Builder builder = S3Client.builder()
                .region(Region.of(settings.region()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(settings.pathStyleAccess())
                        .build());
        URI endpoint = settings.endpoint();
        if (endpoint != null) builder.endpointOverride(endpoint);
        AwsCredentialsProvider credentials = settings.accessKey() != null
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        settings.accessKey(), settings.secretKey()))
                : DefaultCredentialsProvider.create();
        S3Client client = builder.credentialsProvider(credentials).build();
        return new S3CompatibleDocumentStorage(client, settings.bucket());
    }
}
