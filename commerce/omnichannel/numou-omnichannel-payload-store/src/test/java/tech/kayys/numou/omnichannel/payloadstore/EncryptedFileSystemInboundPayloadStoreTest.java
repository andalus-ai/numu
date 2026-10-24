package tech.kayys.numou.omnichannel.payloadstore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.omnichannel.core.ChannelContext;
import tech.kayys.numou.omnichannel.core.ChannelType;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedFileSystemInboundPayloadStoreTest {
    @TempDir Path tempDir;
    private final Map<String, SecretKey> keys = new ConcurrentHashMap<>();
    private final TenantPayloadKeyProvider provider = tenant -> keys.computeIfAbsent(
            tenant.toString(), ignored -> new SecretKeySpec(new byte[32], "AES"));

    @Test
    void encryptsPayloadAndScopesReadsToTenant() throws Exception {
        var store = new EncryptedFileSystemInboundPayloadStore(tempDir, provider);
        ChannelContext tenantA = context("tenant-a");
        byte[] payload = "{\"event\":\"order.updated\"}".getBytes(StandardCharsets.UTF_8);
        var ref = store.store(tenantA, payload, digest(payload), Instant.now().plusSeconds(3600));
        Path file;
        try (var paths = Files.walk(tempDir)) { file = paths.filter(p -> p.toString().endsWith(".payload")).findFirst().orElseThrow(); }
        assertThat(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).doesNotContain(new String(payload, StandardCharsets.UTF_8));
        assertThat(store.load(tenantA, ref)).contains(payload);
        assertThat(store.load(context("tenant-b"), ref)).isEmpty();
    }

    @Test
    void rejectsDigestMismatch() {
        var store = new EncryptedFileSystemInboundPayloadStore(tempDir, provider);
        byte[] payload = "sensitive".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> store.store(context("tenant-a"), payload, "0".repeat(64), Instant.now().plusSeconds(3600)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void purgesExpiredPayload() {
        var store = new EncryptedFileSystemInboundPayloadStore(tempDir, provider);
        ChannelContext context = context("tenant-a");
        byte[] payload = "expire me".getBytes(StandardCharsets.UTF_8);
        var ref = store.store(context, payload, digest(payload), Instant.now().plusSeconds(60));
        assertThat(store.purgeExpired(Instant.now().plusSeconds(120), 10)).isEqualTo(1);
        assertThat(store.load(context, ref)).isEmpty();
    }

    @Test
    void rejectsNonUuidReferences() {
        var store = new EncryptedFileSystemInboundPayloadStore(tempDir, provider);
        assertThatThrownBy(() -> store.load(context("tenant-a"),
                new tech.kayys.numou.omnichannel.core.InboundPayloadReference("../../secret")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ChannelContext context(String tenant) {
        return ChannelContext.of(TenantId.of(tenant), null, ChannelType.WEB, "account", "correlation");
    }

    private static String digest(byte[] payload) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
    }
}