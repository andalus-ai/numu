package tech.kayys.numou.billing.dunning.model;

import tech.kayys.numou.billing.domain.valueobject.DunningAction;
import tech.kayys.numou.billing.domain.valueobject.DunningLevel;

import java.time.Duration;
import java.util.List;

public record DunningPolicy(
    String policyId,
    String name,
    int maxAttempts,
    int gracePeriodDays,
    List<DunningStep> steps
) {
    public record DunningStep(
        int stepNumber,
        DunningLevel level,
        DunningAction action,
        Duration waitBeforeNextStep,
        String templateName
    ) {}

    public static DunningPolicy defaultPolicy() {
        return new DunningPolicy(
            "DEFAULT_POLICY",
            "Standard 4-Step Dunning Policy",
            4,
            5,
            List.of(
                new DunningStep(1, DunningLevel.create(1, "Initial Reminder", 3, DunningAction.EMAIL_REMINDER, "email_reminder_1"), DunningAction.EMAIL_REMINDER, Duration.ofDays(3), "email_reminder_1"),
                new DunningStep(2, DunningLevel.create(2, "Second Reminder", 5, DunningAction.EMAIL_REMINDER, "email_reminder_2"), DunningAction.EMAIL_REMINDER, Duration.ofDays(5), "email_reminder_2"),
                new DunningStep(3, DunningLevel.create(3, "Final Warning", 7, DunningAction.SMS_REMINDER, "sms_warning"), DunningAction.SMS_REMINDER, Duration.ofDays(7), "sms_warning"),
                new DunningStep(4, DunningLevel.create(4, "Suspension Notice", 3, DunningAction.SUSPEND_SERVICE, "suspension_notice"), DunningAction.SUSPEND_SERVICE, Duration.ofDays(3), "suspension_notice")
            )
        );
    }
}
