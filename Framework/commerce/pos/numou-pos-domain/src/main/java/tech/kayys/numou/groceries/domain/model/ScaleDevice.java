package tech.kayys.syirkah.groceries.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.groceries.domain.identifier.ScaleId;
import tech.kayys.syirkah.groceries.domain.valueobject.Weight;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Scale device aggregate root.
 * Represents a physical scale device for weighing pos items.
 */
public final class ScaleDevice extends AbstractAggregateRoot<ScaleId> {

    private static final long serialVersionUID = 1L;

    private String deviceName;
    private String model;
    private String serialNumber;
    private String manufacturer;
    private ScaleType scaleType;
    private Weight maxWeight;
    private Weight minWeight;
    private Weight tareWeight;
    private double accuracyGrams;
    private boolean connected;
    private String ipAddress;
    private int port;
    private String connectionStatus;
    private List<ScaleTransaction> transactions;
    private boolean active;

    private ScaleDevice(ScaleId id) {
        super(id);
        this.transactions = new ArrayList<>();
        this.active = true;
        this.connected = false;
        this.connectionStatus = "OFFLINE";
    }

    private ScaleDevice() {
        super();
    }

    public static ScaleDevice create(
            ScaleId id,
            String deviceName,
            String model,
            ScaleType scaleType,
            Weight maxWeight) {
        ScaleDevice device = new ScaleDevice(id);
        device.deviceName = deviceName;
        device.model = model;
        device.scaleType = scaleType;
        device.maxWeight = maxWeight;
        device.tareWeight = Weight.zero();
        return device;
    }

    public void connect(String ipAddress, int port) {
        this.ipAddress = ipAddress;
        this.port = port;
        this.connected = true;
        this.connectionStatus = "ONLINE";
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void disconnect() {
        this.connected = false;
        this.connectionStatus = "OFFLINE";
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setError(String errorMessage) {
        this.connectionStatus = "ERROR";
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void recordTransaction(ScaleTransaction transaction) {
        transactions.add(transaction);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public boolean isValidWeight(Weight weight) {
        if (weight.isZero()) {
            return false;
        }
        if (minWeight != null && weight.isLessThan(minWeight)) {
            return false;
        }
        if (maxWeight != null && weight.isGreaterThan(maxWeight)) {
            return false;
        }
        return true;
    }

    public Weight getNetWeight(Weight grossWeight) {
        if (!isValidWeight(grossWeight)) {
            throw new IllegalArgumentException("Invalid weight for scale");
        }
        return grossWeight.subtract(tareWeight);
    }

    public String getDeviceName() { return deviceName; }
    public String getModel() { return model; }
    public String getSerialNumber() { return serialNumber; }
    public String getManufacturer() { return manufacturer; }
    public ScaleType getScaleType() { return scaleType; }
    public Weight getMaxWeight() { return maxWeight; }
    public Weight getMinWeight() { return minWeight; }
    public Weight getTareWeight() { return tareWeight; }
    public double getAccuracyGrams() { return accuracyGrams; }
    public boolean isConnected() { return connected; }
    public String getIpAddress() { return ipAddress; }
    public int getPort() { return port; }
    public String getConnectionStatus() { return connectionStatus; }
    public List<ScaleTransaction> getTransactions() { return Collections.unmodifiableList(transactions); }
    public boolean isActive() { return active; }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setMinWeight(Weight minWeight) {
        this.minWeight = minWeight;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setTareWeight(Weight tareWeight) {
        this.tareWeight = tareWeight;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setAccuracyGrams(double accuracyGrams) {
        this.accuracyGrams = accuracyGrams;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setActive(boolean active) {
        this.active = active;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "ScaleDevice{" +
                "id=" + getId() +
                ", deviceName='" + deviceName + '\'' +
                ", scaleType=" + scaleType +
                ", connected=" + connected +
                '}';
    }

    public enum ScaleType {
        DELI("Deli Scale"),
        PRODUCE("Produce Scale"),
        BAKERY("Bakery Scale"),
        GENERAL("General Scale"),
        BULK("Bulk Scale");

        private final String displayName;

        ScaleType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static final class ScaleTransaction implements tech.kayys.syirkah.foundation.domain.valueobject.ValueObject {
        private static final long serialVersionUID = 1L;

        private final String productId;
        private final Weight grossWeight;
        private final Weight netWeight;
        private final Instant timestamp;
        private final String operatorId;
        private final String transactionId;

        public ScaleTransaction(
                String productId,
                Weight grossWeight,
                Weight netWeight,
                Instant timestamp,
                String operatorId,
                String transactionId) {
            this.productId = productId;
            this.grossWeight = grossWeight;
            this.netWeight = netWeight;
            this.timestamp = timestamp;
            this.operatorId = operatorId;
            this.transactionId = transactionId;
            validate();
        }

        @Override
        public void validate() {
            if (productId == null || productId.trim().isEmpty()) {
                throw new IllegalArgumentException("Product ID cannot be empty");
            }
            if (grossWeight == null) {
                throw new IllegalArgumentException("Gross weight cannot be null");
            }
            if (netWeight == null || netWeight.isZero()) {
                throw new IllegalArgumentException("Net weight must be positive");
            }
            if (timestamp == null) {
                throw new IllegalArgumentException("Timestamp cannot be null");
            }
        }

        public String getProductId() { return productId; }
        public Weight getGrossWeight() { return grossWeight; }
        public Weight getNetWeight() { return netWeight; }
        public Instant getTimestamp() { return timestamp; }
        public String getOperatorId() { return operatorId; }
        public String getTransactionId() { return transactionId; }

        @Override
        public String toString() {
            return "ScaleTransaction{" +
                    "productId='" + productId + '\'' +
                    ", netWeight=" + netWeight +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}
