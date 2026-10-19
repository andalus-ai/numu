package tech.kayys.tax.service;

import tech.kayys.tax.entity.Company;
import tech.kayys.tax.entity.TaxCalculation;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class TaxNotificationService {
    
    private static final Logger LOG = Logger.getLogger(TaxNotificationService.class.getName());
    
    @Inject
    Mailer mailer;
    
    public void sendHighTaxLiabilityNotification(Company company, BigDecimal taxAmount) {
        String subject = "High Tax Liability Alert - " + company.companyName;
        String body = String.format(
            "Dear Tax Officer,\n\n" +
            "Company %s (NPWP: %s) has a high tax liability of IDR %s.\n" +
            "Please review this calculation for accuracy.\n\n" +
            "Best regards,\nTax Management System",
            company.companyName, company.npwp, taxAmount.toString()
        );
        
        try {
            mailer.send(Mail.withText("tax-officer@company.com", subject, body));
            LOG.info("High tax liability notification sent for company: " + company.npwp);
        } catch (Exception e) {
            LOG.severe("Failed to send high tax liability notification: " + e.getMessage());
        }
    }
    
    public void sendTaxApprovalNotification(Company company, TaxCalculation calculation) {
        String subject = "Tax Calculation Approved - " + company.companyName;
        String body = String.format(
            "Dear Finance Team,\n\n" +
            "Tax calculation for %s (NPWP: %s) has been approved.\n" +
            "Tax Year: %d\n" +
            "Total Tax Liability: IDR %s\n" +
            "Due Date: %s\n\n" +
            "Please proceed with payment processing.\n\n" +
            "Best regards,\nTax Management System",
            company.companyName, company.npwp, calculation.taxYear, 
            calculation.totalTaxLiability.toString(), calculation.dueDate.toString()
        );
        
        try {
            mailer.send(Mail.withText("finance@company.com", subject, body));
            LOG.info("Tax approval notification sent for company: " + company.npwp);
        } catch (Exception e) {
            LOG.severe("Failed to send tax approval notification: " + e.getMessage());
        }
    }
    
    // Quartz cron: when a day-of-week is given, day-of-month must be "?".
    @Scheduled(cron = "0 0 9 ? * MON") // Every Monday at 9 AM
    public void sendWeeklyTaxReminders() {
        LOG.info("Sending weekly tax reminders");
        
        LocalDate nextWeek = LocalDate.now().plusWeeks(1);
        List<TaxCalculation> upcomingDueTaxes = TaxCalculation.list(
            "dueDate <= ?1 and status != ?2", 
            nextWeek, TaxCalculation.CalculationStatus.PAID
        );
        
        for (TaxCalculation calc : upcomingDueTaxes) {
            sendTaxDueReminder(calc);
        }
    }
    
    private void sendTaxDueReminder(TaxCalculation calculation) {
        String subject = "Tax Payment Due Reminder - " + calculation.company.companyName;
        String body = String.format(
            "Dear Finance Team,\n\n" +
            "This is a reminder that tax payment is due for:\n" +
            "Company: %s (NPWP: %s)\n" +
            "Tax Year: %d\n" +
            "Tax Period: %s\n" +
            "Amount Due: IDR %s\n" +
            "Due Date: %s\n\n" +
            "Please ensure timely payment to avoid penalties.\n\n" +
            "Best regards,\nTax Management System",
            calculation.company.companyName, calculation.company.npwp, 
            calculation.taxYear, calculation.taxPeriod,
            calculation.totalTaxLiability.toString(), calculation.dueDate.toString()
        );
        
        try {
            mailer.send(Mail.withText("finance@company.com", subject, body));
        } catch (Exception e) {
            LOG.severe("Failed to send tax due reminder: " + e.getMessage());
        }
    }
}
