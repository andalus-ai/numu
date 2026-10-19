package tech.kayys.payment.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class NotificationService {
    
    @Inject
    Mailer mailer;
    
    public void sendPaymentStatusNotification(Payment payment) {
        // Send email notification
        sendEmailNotification(payment);
        
        // Send webhook notification to merchant
        sendWebhookNotification(payment);
    }
    
    private void sendEmailNotification(Payment payment) {
        String subject = "Payment Status Update - " + payment.transactionId;
        String body = buildEmailBody(payment);
        
        mailer.send(Mail.withText("customer@example.com", subject, body));
    }
    
    private void sendWebhookNotification(Payment payment) {
        if (payment.callbackUrl != null) {
            // Implementation for sending webhook to merchant
            // This would typically use HTTP client to send POST request
        }
    }
    
    private String buildEmailBody(Payment payment) {
        return String.format(
            "Dear Customer,\n\n" +
            "Your payment with transaction ID %s has been updated.\n" +
            "Status: %s\n" +
            "Amount: IDR %s\n" +
            "Payment Method: %s\n\n" +
            "Thank you for using our service.\n\n" +
            "Best regards,\n" +
            "Payment Team",
            payment.transactionId,
            payment.status.getDisplayName(),
            payment.amount.toString(),
            payment.paymentMethod.getDisplayName()
        );
    }
}