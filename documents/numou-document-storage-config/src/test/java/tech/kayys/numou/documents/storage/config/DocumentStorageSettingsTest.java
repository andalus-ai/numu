package tech.kayys.numou.documents.storage.config;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStorageSettingsTest {
    @Test
    void externalModeDefaultsToRustFsDevelopmentEndpoint() {
        DocumentStorageSettings settings = new DocumentStorageSettings(
                StorageMode.EXTERNAL, null, null, null, null, "documents",
                true, null, null, false);

        assertEquals("rustfs", settings.provider());
        assertEquals(URI.create("http://localhost:9000"), settings.endpoint());
    }

    @Test
    void productionRejectsInsecureExternalEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentStorageSettings(
                StorageMode.EXTERNAL, "rustfs", null, URI.create("http://rustfs.internal:9000"),
                "us-east-1", "documents", true, "access", "secret", true));
    }

    @Test
    void credentialsMustBeConfiguredAsPair() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentStorageSettings(
                StorageMode.EXTERNAL, "rustfs", null, URI.create("https://rustfs.internal"),
                "us-east-1", "documents", true, "access", null, true));
    }

    @Test
    void cloudModeDefaultsToS3WithoutEndpointOverride() {
        DocumentStorageSettings settings = new DocumentStorageSettings(
                StorageMode.CLOUD, null, null, null, null, "documents",
                false, null, null, true);

        assertEquals("s3", settings.provider());
        assertNull(settings.endpoint());
    }

    @Test
    void localModeDoesNotRequireBucket() {
        DocumentStorageSettings settings = DocumentStorageSettings.local(null);
        assertEquals(StorageMode.LOCAL, settings.mode());
        assertNotNull(settings.localRoot());
        assertNull(settings.bucket());
    }
}
