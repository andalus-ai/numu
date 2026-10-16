package tech.kayys.syirkah.kiosk.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskId;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskSessionId;
import tech.kayys.syirkah.kiosk.domain.valueobject.KioskStatus;
import tech.kayys.syirkah.kiosk.domain.valueobject.KioskMode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Kiosk Device aggregate root.
 * Represents a physical self-service kiosk.
 */
public final class KioskDevice extends AbstractAggregateRoot<KioskId> {
    
    private static final long serialVersionUID = 1L;
    
    private String deviceName;
    private String model;
    private String serialNumber;
    private String location;
    private String storeId;
    private KioskStatus status;
    private KioskMode mode;
    private boolean isActive;
    private boolean isCashAccepted;
    private boolean isCardAccepted;
    private boolean isMobilePaymentAccepted;
    private String ipAddress;
    private String softwareVersion;
    private String firmwareVersion;
    private String lastMaintenanceDate;
    private List<KioskEvent> events;
    private List<KioskSession> sessions;
    private String cashDrawerBalance;
    private int thermalPaperRemaining;
    private int receiptPaperRemaining;
    private String notes;

    private KioskDevice(KioskId id) {
        super(id);
        this.status = KioskStatus.ONLINE;
        this.isActive = true;
        this.isCashAccepted = true;
        this.isCardAccepted = true;
        this.isMobilePaymentAccepted = true;
        this.events = new ArrayList<>();
        this.sessions = new ArrayList<>();
        this.thermalPaperRemaining = 100;
        this.receiptPaperRemaining = 100;
        this.mode = KioskMode.SELF_CHECKOUT;
    }

    private KioskDevice() {
        super();
    }

    /**
     * Factory method to create a new kiosk device.
     */
    public static KioskDevice create(
            KioskId id,
            String deviceName,
            String model,
            String location,
            String storeId) {
        KioskDevice device = new KioskDevice(id);
        device.deviceName = deviceName;
        device.model = model;
        device.location = location;
        device.storeId = storeId;
        return device;
    }

    /**
     * Updates kiosk status.
     */
    public void updateStatus(KioskStatus status) {
        this.status = status;
        if (status == KioskStatus.ERROR || status == KioskStatus.MAINTENANCE) {
            this.isActive = false;
        } else {
            this.isActive = true;
        }
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets kiosk mode.
     */
    public void setMode(KioskMode mode) {
        this.mode = mode;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Starts a kiosk session.
     */
    public void startSession(KioskSession session) {
        if (!isActive || status != KioskStatus.ONLINE) {
            throw new IllegalStateException("Kiosk is not available");
        }
        sessions.add(session);
        this.status = KioskStatus.IN_USE;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Ends a kiosk session.
     */
    public void endSession(KioskSessionId sessionId) {
        KioskSession session = sessions.stream()
            .filter(s -> s.getId().equals(sessionId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        
        session.end();
        this.status = KioskStatus.ONLINE;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds a kiosk event.
     */
    public void addEvent(KioskEvent event) {
        events.add(event);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Updates paper levels.
     */
    public void updatePaperLevels(int thermalPaper, int receiptPaper) {
        this.thermalPaperRemaining = thermalPaper;
        this.receiptPaperRemaining = receiptPaper;
        
        if (thermalPaper < 10 || receiptPaper < 10) {
            this.status = KioskStatus.LOW_PAPER;
        } else if (thermalPaper < 20 || receiptPaper < 20) {
            this.status = KioskStatus.LOW_THERMAL;
        }
        
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Updates cash drawer balance.
     */
    public void updateCashDrawer(String balance) {
        this.cashDrawerBalance = balance;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Gets active session count.
     */
    public int getActiveSessions() {
        return (int) sessions.stream()
            .filter(KioskSession::isActive)
            .count();
    }

    /**
     * Gets session duration average.
     */
    public double getAverageSessionDurationMinutes() {
        return sessions.stream()
            .filter(s -> s.getEndedAt() != null)
            .mapToLong(KioskSession::getDurationSeconds)
            .average()
            .orElse(0.0) / 60.0;
    }

    // Getters and Setters
    public String getDeviceName() { return deviceName; }
    public String getModel() { return model; }
    public String getSerialNumber() { return serialNumber; }
    public String getLocation() { return location; }
    public String getStoreId() { return storeId; }
    public KioskStatus getStatus() { return status; }
    public KioskMode getMode() { return mode; }
    public boolean isActive() { return isActive; }
    public boolean isCashAccepted() { return isCashAccepted; }
    public boolean isCardAccepted() { return isCardAccepted; }
    public boolean isMobilePaymentAccepted() { return isMobilePaymentAccepted; }
    public String getIpAddress() { return ipAddress; }
    public String getSoftwareVersion() { return softwareVersion; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public String getLastMaintenanceDate() { return lastMaintenanceDate; }
    public List<KioskEvent> getEvents() { return Collections.unmodifiableList(events); }
    public List<KioskSession> getSessions() { return Collections.unmodifiableList(sessions); }
    public String getCashDrawerBalance() { return cashDrawerBalance; }
    public int getThermalPaperRemaining() { return thermalPaperRemaining; }
    public int getReceiptPaperRemaining() { return receiptPaperRemaining; }
    public String getNotes() { return notes; }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setFirmwareVersion(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setLastMaintenanceDate(String lastMaintenanceDate) {
        this.lastMaintenanceDate = lastMaintenanceDate;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCashAccepted(boolean cashAccepted) {
        isCashAccepted = cashAccepted;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCardAccepted(boolean cardAccepted) {
        isCardAccepted = cardAccepted;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setMobilePaymentAccepted(boolean mobilePaymentAccepted) {
        isMobilePaymentAccepted = mobilePaymentAccepted;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setNotes(String notes) {
        this.notes = notes;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "KioskDevice{" +
                "id=" + getId() +
                ", deviceName='" + deviceName + '\'' +
                ", location='" + location + '\'' +
                ", status=" + status +
                '}';
    }

    /**
     * Kiosk event record.
     */
    public static final class KioskEvent implements ValueObject {
        private static final long serialVersionUID = 1L;
        
        private final String eventType;
        private final String description;
        private final Instant timestamp;
        private final String severity; // INFO, WARNING, ERROR, CRITICAL
        private final String source;

        public KioskEvent(String eventType, String description, String severity, String source) {
            this.eventType = eventType;
            this.description = description;
            this.timestamp = Instant.now();
            this.severity = severity;
            this.source = source;
            validate();
        }

        @Override
        public void validate() {
            if (eventType == null || eventType.trim().isEmpty()) {
                throw new IllegalArgumentException("Event type cannot be empty");
            }
            if (severity == null || severity.trim().isEmpty()) {
                throw new IllegalArgumentException("Severity cannot be empty");
            }
        }

        public String getEventType() { return eventType; }
        public String getDescription() { return description; }
        public Instant getTimestamp() { return timestamp; }
        public String getSeverity() { return severity; }
        public String getSource() { return source; }

        @Override
        public String toString() {
            return "KioskEvent{" +
                    "eventType='" + eventType + '\'' +
                    ", severity='" + severity + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}