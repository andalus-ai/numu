package tech.kayys.syirkah.document.adapter.outbound.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import tech.kayys.syirkah.document.application.port.DocumentStoragePort;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class S3DocumentStorageTest {
    @Test
    void presigns_an_immutable_put_with_required_headers() {
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create("access-key", "secret-key")
        );
        var serviceConfiguration = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        var endpoint = URI.create("http://localhost:9000");
        var client = S3Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfiguration)
                .endpointOverride(endpoint)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        var presigner = S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfiguration)
                .endpointOverride(endpoint)
                .build();
        var storage = new S3DocumentStorage("documents", 1024, client, presigner);
        var tenantId = UUID.randomUUID();
        var sessionId = UUID.randomUUID();
        var expiry = Instant.now().plusSeconds(60);

        try {
            var target = storage.createUploadTarget(new DocumentStoragePort.UploadTargetRequest(
                    tenantId.toString(),
                    sessionId.toString(),
                    "ignored.pdf",
                    "application/pdf",
                    10,
                    null,
                    expiry
            )).await().indefinitely();

            var signedHeaders = URI.create(target.uploadUrl()).getRawQuery();
            assertTrue(signedHeaders.contains("X-Amz-SignedHeaders=")
                    && signedHeaders.contains("if-none-match"), target.uploadUrl());
            assertTrue(target.uploadUrl().startsWith("http://localhost:9000/documents/"), target.uploadUrl());
            assertEquals(tenantId + "/" + sessionId, target.storageKey());
            assertEquals("application/pdf", target.requiredHeaders().get("Content-Type"));
            assertEquals("*", target.requiredHeaders().get("If-None-Match"));
            assertEquals(expiry, target.expiresAt());
        } finally {
            storage.closeClients();
        }
    }

    @Test
    void rejects_non_uuid_keys() {
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create("access-key", "secret-key")
        );
        var client = S3Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        var presigner = S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .build();
        var storage = new S3DocumentStorage("documents", 1024, client, presigner);

        try {
            assertThrows(IllegalArgumentException.class,
                    () -> storage.delete("../../outside").await().indefinitely());
        } finally {
            storage.closeClients();
        }
    }
}
