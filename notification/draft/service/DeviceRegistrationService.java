package id.mindo.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import id.mindo.notification.dto.DeviceRegistrationRequest;
import id.mindo.notification.dto.PushDeviceRecord;
import id.mindo.notification.dto.PushDeviceSummary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class DeviceRegistrationService {

    private static final Logger LOG = Logger.getLogger(DeviceRegistrationService.class);

    private final Map<String, Map<String, PushDeviceRecord>> devicesByClientId = new ConcurrentHashMap<>();

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "mindo.push.devices.file", defaultValue = "data/push-devices.json")
    String devicesFile;

    @PostConstruct
    void load() {
        final Path path = Path.of(devicesFile);
        if (!Files.exists(path)) {
            return;
        }
        try {
            final List<PushDeviceRecord> records = objectMapper.readValue(
                    path.toFile(),
                    new TypeReference<List<PushDeviceRecord>>() {
                    });
            for (PushDeviceRecord record : records) {
                final String clientId = normalize(record.getClientId());
                final String token = normalize(record.getToken());
                if (clientId.isEmpty() || token.isEmpty()) {
                    continue;
                }
                devicesByClientId
                        .computeIfAbsent(clientId, ignored -> new ConcurrentHashMap<>())
                        .put(token, record);
            }
            LOG.infov("Loaded push device registrations count={0}", records.size());
        } catch (Exception e) {
            LOG.warnv(e, "Failed loading push device registrations from {0}", path);
        }
    }

    public PushDeviceRecord register(DeviceRegistrationRequest request) {
        final String clientId = normalize(request.getClientId());
        final String token = normalize(request.getToken());
        if (clientId.isEmpty() || token.isEmpty()) {
            throw new IllegalArgumentException("clientId and token are required");
        }

        final PushDeviceRecord record = new PushDeviceRecord(
                clientId,
                token,
                normalize(request.getPlatform()),
                normalize(request.getAppInstance()),
                OffsetDateTime.now());

        devicesByClientId
                .computeIfAbsent(clientId, ignored -> new ConcurrentHashMap<>())
                .put(token, record);
        persist();
        return record;
    }

    public boolean unregister(String clientId, String token) {
        final String normalizedClientId = normalize(clientId);
        final String normalizedToken = normalize(token);
        final Map<String, PushDeviceRecord> devices = devicesByClientId.get(normalizedClientId);
        if (devices == null) {
            return false;
        }
        final PushDeviceRecord removed = devices.remove(normalizedToken);
        if (devices.isEmpty()) {
            devicesByClientId.remove(normalizedClientId);
        }
        if (removed != null) {
            persist();
        }
        return removed != null;
    }

    public boolean unregister(PushDeviceRecord record) {
        if (record == null) {
            return false;
        }
        return unregister(record.getClientId(), record.getToken());
    }

    public List<PushDeviceRecord> listByClientId(String clientId) {
        final Map<String, PushDeviceRecord> devices = devicesByClientId.get(normalize(clientId));
        if (devices == null || devices.isEmpty()) {
            return List.of();
        }
        final List<PushDeviceRecord> out = new ArrayList<>(devices.values());
        out.sort(Comparator.comparing(PushDeviceRecord::getUpdatedAt).reversed());
        return out;
    }

    public List<PushDeviceSummary> listSummariesByClientId(String clientId) {
        return listByClientId(clientId)
                .stream()
                .map(PushDeviceSummary::fromRecord)
                .toList();
    }

    public int registeredClientCount() {
        return devicesByClientId.size();
    }

    public int registeredDeviceCount() {
        return devicesByClientId.values()
                .stream()
                .mapToInt(Map::size)
                .sum();
    }

    private synchronized void persist() {
        final Path path = Path.of(devicesFile);
        final List<PushDeviceRecord> records = devicesByClientId
                .values()
                .stream()
                .flatMap(devices -> devices.values().stream())
                .sorted(Comparator.comparing(PushDeviceRecord::getUpdatedAt).reversed())
                .toList();
        try {
            final Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            final Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), records);
            Files.move(
                    tmp,
                    path,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | UnsupportedOperationException e) {
            LOG.warnv(e, "Failed persisting push device registrations to {0}", path);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
