package tech.kayys.numou.fms.domain.model;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.fms.domain.identifier.FuelTransactionId;



import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fuel Transaction aggregate root.
 * Manages fuel purchases, fuel cards, and fuel consumption.
 */
public final class FuelTransaction extends AbstractAggregateRoot<FuelTransactionId> {
    
    private static final long serialVersionUID = 1L;
    
    private String vehicleId;
    private String driverId;
    private String fuelCardId;
    private String transactionNumber;
    private Instant transactionDate;
    private String stationName;
    private String stationAddress;
    private String fuelType; // PETROL, DIESEL, ELECTRIC, LPG, CNG, HYDROGEN
    private double quantity;
    private String quantityUnit; // LITERS, GALLONS, KWH
    private double unitPrice;
    private double totalAmount;
    private double discountAmount;
    private double taxAmount;
    private double netAmount;
    private String currencyCode;
    private double odometerReading;
    private FuelTransactionStatus status;
    private String authorizationCode;
    private String transactionId;
    private String fuelCardNumberMasked;
    private String promotionCode;
    private List<FuelTransaction> relatedTransactions;
    private String notes;
    private String createdBy;
    private boolean active;

    private FuelTransaction(FuelTransactionId id) {
        super(id);
        this.relatedTransactions = new ArrayList<>();
        this.status = FuelTransactionStatus.PENDING;
        this.active = true;
        this.transactionDate = Instant.now();
    }

    private FuelTransaction() {
        super();
    }

    /**
     * Factory method to create a new fuel transaction.
     */
    public static FuelTransaction create(
            FuelTransactionId id,
            String vehicleId,
            String driverId,
            String fuelCardId,
            String stationName,
            String fuelType,
            double quantity,
            double unitPrice,
            String currencyCode) {
        FuelTransaction transaction = new FuelTransaction(id);
        transaction.vehicleId = vehicleId;
        transaction.driverId = driverId;
        transaction.fuelCardId = fuelCardId;
        transaction.stationName = stationName;
        transaction.fuelType = fuelType;
        transaction.quantity = quantity;
        transaction.unitPrice = unitPrice;
        transaction.currencyCode = currencyCode;
        transaction.totalAmount = quantity * unitPrice;
        transaction.transactionNumber = generateTransactionNumber();
        return transaction;
    }

    /**
     * Approves the fuel transaction.
     */
    public void approve(String approvedBy) {
        if (status != FuelTransactionStatus.PENDING) {
            throw new IllegalStateException("Cannot approve transaction in status: " + status);
        }
        this.status = FuelTransactionStatus.APPROVED;
        this.notes = "Approved by: " + approvedBy;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Rejects the fuel transaction.
     */
    public void reject(String reason) {
        if (status != FuelTransactionStatus.PENDING) {
            throw new IllegalStateException("Cannot reject transaction in status: " + status);
        }
        this.status = FuelTransactionStatus.REJECTED;
        this.notes = "Rejected: " + reason;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Captures the fuel transaction.
     */
    public void capture(String authorizationCode, String transactionId) {
        if (status != FuelTransactionStatus.APPROVED && status != FuelTransactionStatus.PENDING) {
            throw new IllegalStateException("Cannot capture transaction in status: " + status);
        }
        this.status = FuelTransactionStatus.COMPLETED;
        this.authorizationCode = authorizationCode;
        this.transactionId = transactionId;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Calculates fuel efficiency for this transaction.
     */
    public double calculateFuelEfficiency(double distanceSinceLastFill) {
        if (quantity == 0 || distanceSinceLastFill == 0) {
            return 0.0;
        }
        return distanceSinceLastFill / quantity;
    }

    private static String generateTransactionNumber() {
        return "FUEL-" + System.currentTimeMillis() + "-" + 
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    // Getters
    public String getVehicleId() { return vehicleId; }
    public String getDriverId() { return driverId; }
    public String getFuelCardId() { return fuelCardId; }
    public String getTransactionNumber() { return transactionNumber; }
    public Instant getTransactionDate() { return transactionDate; }
    public String getStationName() { return stationName; }
    public String getStationAddress() { return stationAddress; }
    public String getFuelType() { return fuelType; }
    public double getQuantity() { return quantity; }
    public String getQuantityUnit() { return quantityUnit; }
    public double getUnitPrice() { return unitPrice; }
    public double getTotalAmount() { return totalAmount; }
    public double getDiscountAmount() { return discountAmount; }
    public double getTaxAmount() { return taxAmount; }
    public double getNetAmount() { return netAmount; }
    public String getCurrencyCode() { return currencyCode; }
    public double getOdometerReading() { return odometerReading; }
    public FuelTransactionStatus getStatus() { return status; }
    public String getAuthorizationCode() { return authorizationCode; }
    public String getTransactionId() { return transactionId; }
    public String getFuelCardNumberMasked() { return fuelCardNumberMasked; }
    public String getPromotionCode() { return promotionCode; }
    public List<FuelTransaction> getRelatedTransactions() { return Collections.unmodifiableList(relatedTransactions); }
    public String getNotes() { return notes; }
    public String getCreatedBy() { return createdBy; }
    public boolean isActive() { return active; }

    public void setStationAddress(String stationAddress) {
        this.stationAddress = stationAddress;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setQuantityUnit(String quantityUnit) {
        this.quantityUnit = quantityUnit;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setDiscountAmount(double discountAmount) {
        this.discountAmount = discountAmount;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setTaxAmount(double taxAmount) {
        this.taxAmount = taxAmount;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setNetAmount(double netAmount) {
        this.netAmount = netAmount;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setOdometerReading(double odometerReading) {
        this.odometerReading = odometerReading;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setFuelCardNumberMasked(String fuelCardNumberMasked) {
        this.fuelCardNumberMasked = fuelCardNumberMasked;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setPromotionCode(String promotionCode) {
        this.promotionCode = promotionCode;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "FuelTransaction{" +
                "id=" + getId() +
                ", vehicleId='" + vehicleId + '\'' +
                ", fuelType='" + fuelType + '\'' +
                ", quantity=" + quantity +
                ", status=" + status +
                '}';
    }

    /**
     * Fuel transaction status enum.
     */
    public enum FuelTransactionStatus {
        PENDING("Pending"),
        APPROVED("Approved"),
        REJECTED("Rejected"),
        COMPLETED("Completed"),
        CANCELLED("Cancelled");

        private final String description;

        FuelTransactionStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Fuel card value object.
     */
    public static final class FuelCard {
        private final String cardId;
        private final String cardNumberMasked;
        private final String cardType; // COMPANY, DRIVER, FLEET
        private final String issuer;
        private final double creditLimit;
        private final double currentBalance;
        private final String currencyCode;
        private final Instant expiryDate;
        private final boolean active;
        private final List<String> allowedFuelTypes;
        private final List<String> allowedStations;
        private final double dailyLimit;
        private final double weeklyLimit;
        private final double monthlyLimit;
        private final double currentDailyUsage;
        private final double currentWeeklyUsage;
        private final double currentMonthlyUsage;

        public FuelCard(
                String cardId,
                String cardNumberMasked,
                String cardType,
                String issuer,
                double creditLimit,
                double currentBalance,
                String currencyCode,
                Instant expiryDate,
                boolean active,
                List<String> allowedFuelTypes,
                List<String> allowedStations,
                double dailyLimit,
                double weeklyLimit,
                double monthlyLimit,
                double currentDailyUsage,
                double currentWeeklyUsage,
                double currentMonthlyUsage) {
            this.cardId = cardId;
            this.cardNumberMasked = cardNumberMasked;
            this.cardType = cardType;
            this.issuer = issuer;
            this.creditLimit = creditLimit;
            this.currentBalance = currentBalance;
            this.currencyCode = currencyCode;
            this.expiryDate = expiryDate;
            this.active = active;
            this.allowedFuelTypes = allowedFuelTypes != null ? new ArrayList<>(allowedFuelTypes) : new ArrayList<>();
            this.allowedStations = allowedStations != null ? new ArrayList<>(allowedStations) : new ArrayList<>();
            this.dailyLimit = dailyLimit;
            this.weeklyLimit = weeklyLimit;
            this.monthlyLimit = monthlyLimit;
            this.currentDailyUsage = currentDailyUsage;
            this.currentWeeklyUsage = currentWeeklyUsage;
            this.currentMonthlyUsage = currentMonthlyUsage;
        }

        public String getCardId() { return cardId; }
        public String getCardNumberMasked() { return cardNumberMasked; }
        public String getCardType() { return cardType; }
        public String getIssuer() { return issuer; }
        public double getCreditLimit() { return creditLimit; }
        public double getCurrentBalance() { return currentBalance; }
        public String getCurrencyCode() { return currencyCode; }
        public Instant getExpiryDate() { return expiryDate; }
        public boolean isActive() { return active; }
        public List<String> getAllowedFuelTypes() { return Collections.unmodifiableList(allowedFuelTypes); }
        public List<String> getAllowedStations() { return Collections.unmodifiableList(allowedStations); }
        public double getDailyLimit() { return dailyLimit; }
        public double getWeeklyLimit() { return weeklyLimit; }
        public double getMonthlyLimit() { return monthlyLimit; }
        public double getCurrentDailyUsage() { return currentDailyUsage; }
        public double getCurrentWeeklyUsage() { return currentWeeklyUsage; }
        public double getCurrentMonthlyUsage() { return currentMonthlyUsage; }

        public double getDailyRemaining() { return dailyLimit - currentDailyUsage; }
        public double getWeeklyRemaining() { return weeklyLimit - currentWeeklyUsage; }
        public double getMonthlyRemaining() { return monthlyLimit - currentMonthlyUsage; }

        public boolean isExpired() {
            return expiryDate != null && Instant.now().isAfter(expiryDate);
        }

        public boolean canAuthorize(double amount) {
            if (!active || isExpired()) return false;
            if (currentBalance + amount > creditLimit) return false;
            if (currentDailyUsage + amount > dailyLimit) return false;
            if (currentWeeklyUsage + amount > weeklyLimit) return false;
            if (currentMonthlyUsage + amount > monthlyLimit) return false;
            return true;
        }
    }
}