package tech.kayys.numou.notification.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.notification.application.port.PushDeviceRepository;
import tech.kayys.numou.notification.domain.PushDevice;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PushDeviceServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-28T00:00:00Z");

    @Test
    void registrationUsesInjectedClockAndAuthenticatedScopeArguments() {
        RecordingRepository repository = new RecordingRepository();
        PushDeviceService service = new PushDeviceService(repository,
                Clock.fixed(now, ZoneOffset.UTC));

        PushDevice result = service.register(tenantId, recipientId, "token",
                PushDevice.Platform.ANDROID, "phone-1");

        assertEquals(tenantId, result.tenantId());
        assertEquals(recipientId, result.recipientId());
        assertEquals(now, result.createdAt());
        assertEquals("token", repository.registered.token());
    }

    @Test
    void registrationManagementUseCaseReturnsOnlySafeSummary() {
        RecordingRepository repository = new RecordingRepository();
        PushDeviceService service = new PushDeviceService(repository, Clock.fixed(now, ZoneOffset.UTC));

        PushDeviceSummary summary = service.registerSummary(tenantId, recipientId,
                "secret-token-value", PushDevice.Platform.WEB, "browser");

        assertEquals(tenantId, summary.tenantId());
        assertEquals(recipientId, summary.recipientId());
        assertEquals(PushDevice.Platform.WEB, summary.platform());
        assertFalse(summary.toString().contains("secret-token-value"));
        assertEquals("secret-token-value", repository.registered.token());
    }

    @Test
    void activeDeviceListIsImmutableAndRequiresScope() {
        RecordingRepository repository = new RecordingRepository();
        repository.devices.add(PushDevice.register(tenantId, recipientId, "token",
                PushDevice.Platform.IOS, "", now));
        PushDeviceService service = new PushDeviceService(repository,
                Clock.fixed(now, ZoneOffset.UTC));

        List<PushDevice> devices = service.activeDevices(tenantId, recipientId);
        assertEquals(1, devices.size());
        assertThrows(UnsupportedOperationException.class, () -> devices.clear());
        assertThrows(NullPointerException.class, () -> service.activeDevices(tenantId, null));
    }

    @Test
    void unregisterPassesTenantRecipientScopeToRepository() {
        RecordingRepository repository = new RecordingRepository();
        PushDeviceService service = new PushDeviceService(repository,
                Clock.fixed(now, ZoneOffset.UTC));
        UUID deviceId = UUID.randomUUID();

        assertTrue(service.unregister(tenantId, recipientId, deviceId).isEmpty());
        assertEquals(tenantId, repository.lastTenantId);
        assertEquals(recipientId, repository.lastRecipientId);
        assertEquals(deviceId, repository.lastDeviceId);
        assertEquals(now, repository.lastDisabledAt);
    }

    @Test
    void managementSummariesExposeMetadataButNeverProviderTokens() {
        RecordingRepository repository = new RecordingRepository();
        repository.devices.add(PushDevice.register(tenantId, recipientId, "secret-web-subscription",
                PushDevice.Platform.WEB, "browser-1", now));
        PushDeviceService service = new PushDeviceService(repository, Clock.fixed(now, ZoneOffset.UTC));

        List<PushDeviceSummary> summaries = service.activeDeviceSummaries(tenantId, recipientId);

        assertEquals(1, summaries.size());
        PushDeviceSummary summary = summaries.getFirst();
        assertEquals(tenantId, summary.tenantId());
        assertEquals(recipientId, summary.recipientId());
        assertEquals(PushDevice.Platform.WEB, summary.platform());
        assertEquals("browser-1", summary.appInstance());
        assertTrue(summary.active());
        assertFalse(summary.toString().contains("secret-web-subscription"));
    }

    @Test
    void safeUnregisterReturnsMetadataOnlyAndPreservesScope() {
        RecordingRepository repository = new RecordingRepository();
        PushDevice device = PushDevice.register(tenantId, recipientId, "secret-token",
                PushDevice.Platform.ANDROID, "phone-2", now);
        repository.devices.add(device);
        repository.disabledSummary = Optional.of(PushDeviceSummary.from(device.disable(now.plusSeconds(1))));
        PushDeviceService service = new PushDeviceService(repository, Clock.fixed(now, ZoneOffset.UTC));

        PushDeviceSummary result = service.unregisterSummary(tenantId, recipientId, device.id()).orElseThrow();

        assertEquals(device.id(), result.id());
        assertFalse(result.active());
        assertEquals(tenantId, repository.lastTenantId);
        assertEquals(recipientId, repository.lastRecipientId);
        assertEquals(device.id(), repository.lastDeviceId);
        assertEquals(now, repository.lastDisabledAt);
    }

    private static final class RecordingRepository implements PushDeviceRepository {
        private final List<PushDevice> devices = new ArrayList<>();
        private PushDevice registered;
        private UUID lastTenantId;
        private UUID lastRecipientId;
        private UUID lastDeviceId;
        private Instant lastDisabledAt;
        private Optional<PushDeviceSummary> disabledSummary = Optional.empty();

        @Override
        public PushDevice registerOrRefresh(PushDevice device) {
            registered = device;
            return device;
        }

        @Override
        public List<PushDevice> findActiveByRecipient(UUID tenantId, UUID recipientId) {
            lastTenantId = tenantId;
            lastRecipientId = recipientId;
            return devices.stream().filter(PushDevice::isActive).toList();
        }

        @Override
        public Optional<PushDevice> disable(UUID tenantId, UUID recipientId, UUID deviceId, Instant disabledAt) {
            lastTenantId = tenantId;
            lastRecipientId = recipientId;
            lastDeviceId = deviceId;
            lastDisabledAt = disabledAt;
            return Optional.empty();
        }

        @Override
        public Optional<PushDeviceSummary> disableSummary(
                UUID tenantId, UUID recipientId, UUID deviceId, Instant disabledAt) {
            lastTenantId = tenantId;
            lastRecipientId = recipientId;
            lastDeviceId = deviceId;
            lastDisabledAt = disabledAt;
            return disabledSummary;
        }
    }
}
