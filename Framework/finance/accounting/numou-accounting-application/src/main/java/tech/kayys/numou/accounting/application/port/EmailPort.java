package tech.kayys.syirkah.accounting.application.port;

import java.util.concurrent.CompletionStage;

/**
 * Port for sending emails.
 */
public interface EmailPort {

    /**
     * Sends an invoice email with PDF attachment.
     */
    CompletionStage<Boolean> sendInvoiceEmail(
        String to,
        String subject,
        String body,
        byte[] pdfAttachment,
        String pdfFileName
    );

    /**
     * Sends a simple email.
     */
    CompletionStage<Boolean> sendEmail(
        String to,
        String subject,
        String body
    );
}