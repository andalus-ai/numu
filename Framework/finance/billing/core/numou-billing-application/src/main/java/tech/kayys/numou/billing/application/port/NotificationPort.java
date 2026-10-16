package tech.kayys.numou.billing.application.port;

import java.util.concurrent.CompletionStage;

/**
 * Port for sending notifications.
 */
public interface NotificationPort {

    /**
     * Sends an email notification.
     */
    CompletionStage<Void> sendEmail(String to, String subject, String body);

    /**
     * Sends an SMS notification.
     */
    CompletionStage<Void> sendSms(String phoneNumber, String message);

    /**
     * Sends a billing reminder.
     */
    CompletionStage<Void> sendBillingReminder(String customerId, String amount, String dueDate);
}