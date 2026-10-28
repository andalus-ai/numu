package id.mindo.notification.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import id.mindo.notification.dto.NotificationMessage;
import id.mindo.notification.dto.PushDeviceRecord;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class PushDispatchService {

    private static final Logger LOG = Logger.getLogger(PushDispatchService.class);

    private final DeviceRegistrationService deviceRegistrationService;
    private final FcmPushService fcmPushService;

    @ConfigProperty(name = "mindo.push.fcm.enabled", defaultValue = "false")
    boolean fcmEnabled;

    public PushDispatchService(
            DeviceRegistrationService deviceRegistrationService,
            FcmPushService fcmPushService) {
        this.deviceRegistrationService = deviceRegistrationService;
        this.fcmPushService = fcmPushService;
    }

    public int dispatchToClient(String clientId, NotificationMessage notification) {
        final String normalizedClientId = clientId == null ? "" : clientId.trim();
        if (normalizedClientId.isEmpty()) {
            return 0;
        }
        final List<PushDeviceRecord> devices = deviceRegistrationService.listByClientId(normalizedClientId);
        if (devices.isEmpty()) {
            LOG.infov(
                    "Push dispatch skipped because no registered devices. clientId={0}, devices=0, type={1}",
                    normalizedClientId,
                    notification.getType());
            return 0;
        }

        if (!fcmEnabled) {
            LOG.infov(
                    "Push dispatch skipped because FCM is disabled. clientId={0}, devices={1}, type={2}",
                    normalizedClientId,
                    devices.size(),
                    notification.getType());
            return 0;
        }

        if (!fcmPushService.isConfigured()) {
            LOG.warnv(
                    "Push dispatch skipped because FCM credentials are not configured. clientId={0}, devices={1}, type={2}",
                    normalizedClientId,
                    devices.size(),
                    notification.getType());
            return 0;
        }

        CompletableFuture.runAsync(() -> dispatchDevices(normalizedClientId, devices, notification));
        return devices.size();
    }

    public boolean isFcmEnabled() {
        return fcmEnabled;
    }

    public boolean isFcmConfigured() {
        return fcmPushService != null && fcmPushService.isConfigured();
    }

    private void dispatchDevices(
            String clientId,
            List<PushDeviceRecord> devices,
            NotificationMessage notification) {
        int successCount = 0;
        int prunedCount = 0;
        for (PushDeviceRecord device : devices) {
            final FcmPushService.FcmPushResult result = fcmPushService.sendToDevice(device, notification);
            switch (result) {
                case SENT -> successCount++;
                case INVALID_TOKEN -> {
                    if (deviceRegistrationService.unregister(device)) {
                        prunedCount++;
                    }
                }
                case FAILED_RETRYABLE -> {
                }
            }
        }
        LOG.infov(
                "Push dispatch completed. clientId={0}, success={1}/{2}, pruned={3}, type={4}",
                clientId,
                successCount,
                devices.size(),
                prunedCount,
                notification.getType());
    }
}
