package tech.kayys.numou.billing.domain.valueobject;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

import java.util.List;

/**
 * Payment retry configuration.
 */
public final class PaymentRetryConfig implements ValueObject {
    
    private static final long serialVersionUID = 1L;
    
    private final int maxRetries;
    private final List<Double> retryDelaysDays; // Exponential backoff
    private final RetryStrategy strategy;
    private final int maxRetriesPerTransaction;
    private final boolean autoCancelAfterMaxRetries;
    private final boolean sendRetryNotifications;
    private final String notificationTemplate;

    public PaymentRetryConfig(
            int maxRetries,
            List<Double> retryDelaysDays,
            RetryStrategy strategy,
            int maxRetriesPerTransaction,
            boolean autoCancelAfterMaxRetries,
            boolean sendRetryNotifications,
            String notificationTemplate) {
        this.maxRetries = maxRetries;
        this.retryDelaysDays = retryDelaysDays;
        this.strategy = strategy;
        this.maxRetriesPerTransaction = maxRetriesPerTransaction;
        this.autoCancelAfterMaxRetries = autoCancelAfterMaxRetries;
        this.sendRetryNotifications = sendRetryNotifications;
        this.notificationTemplate = notificationTemplate;
        validate();
    }

    @Override
    public void validate() {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("Max retries cannot be negative");
        }
        if (retryDelaysDays == null || retryDelaysDays.isEmpty()) {
            throw new IllegalArgumentException("Retry delays cannot be empty");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("Retry strategy cannot be null");
        }
    }

    // Getters
    public int getMaxRetries() { return maxRetries; }
    public List<Double> getRetryDelaysDays() { return retryDelaysDays; }
    public RetryStrategy getStrategy() { return strategy; }
    public int getMaxRetriesPerTransaction() { return maxRetriesPerTransaction; }
    public boolean isAutoCancelAfterMaxRetries() { return autoCancelAfterMaxRetries; }
    public boolean isSendRetryNotifications() { return sendRetryNotifications; }
    public String getNotificationTemplate() { return notificationTemplate; }

    public double getNextRetryDelay(int attempt) {
        if (attempt >= retryDelaysDays.size()) {
            return retryDelaysDays.get(retryDelaysDays.size() - 1);
        }
        return retryDelaysDays.get(attempt);
    }

    public boolean shouldRetry(int attempt) {
        return attempt < maxRetries;
    }

    /**
     * Retry strategy enum.
     */
    public enum RetryStrategy {
        LINEAR("Linear - Equal intervals"),
        EXPONENTIAL("Exponential - Increasing intervals"),
        CUSTOM("Custom - Defined intervals");

        private final String description;

        RetryStrategy(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int maxRetries = 3;
        private List<Double> retryDelaysDays = List.of(1.0, 2.0, 4.0);
        private RetryStrategy strategy = RetryStrategy.EXPONENTIAL;
        private int maxRetriesPerTransaction = 1;
        private boolean autoCancelAfterMaxRetries = true;
        private boolean sendRetryNotifications = true;
        private String notificationTemplate = "payment-retry";

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder retryDelaysDays(List<Double> retryDelaysDays) {
            this.retryDelaysDays = retryDelaysDays;
            return this;
        }

        public Builder strategy(RetryStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder maxRetriesPerTransaction(int maxRetriesPerTransaction) {
            this.maxRetriesPerTransaction = maxRetriesPerTransaction;
            return this;
        }

        public Builder autoCancelAfterMaxRetries(boolean autoCancelAfterMaxRetries) {
            this.autoCancelAfterMaxRetries = autoCancelAfterMaxRetries;
            return this;
        }

        public Builder sendRetryNotifications(boolean sendRetryNotifications) {
            this.sendRetryNotifications = sendRetryNotifications;
            return this;
        }

        public Builder notificationTemplate(String notificationTemplate) {
            this.notificationTemplate = notificationTemplate;
            return this;
        }

        public PaymentRetryConfig build() {
            return new PaymentRetryConfig(
                maxRetries, retryDelaysDays, strategy, maxRetriesPerTransaction,
                autoCancelAfterMaxRetries, sendRetryNotifications, notificationTemplate
            );
        }
    }
}