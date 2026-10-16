package tech.kayys.numou.billing.application.api;

import tech.kayys.numou.billing.application.api.command.*;
import tech.kayys.numou.billing.application.api.query.*;
import tech.kayys.numou.billing.domain.identifier.BillingScheduleId;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Public API for billing operations.
 */
public interface BillingService {

    // ============ Billing Schedule Operations ============

    /**
     * Creates a billing schedule.
     */
    CompletionStage<BillingScheduleId> createBillingSchedule(CreateBillingScheduleCommand command);

    /**
     * Activates a billing schedule.
     */
    CompletionStage<BillingScheduleId> activateBillingSchedule(ActivateBillingScheduleCommand command);

    /**
     * Pauses a billing schedule.
     */
    CompletionStage<BillingScheduleId> pauseBillingSchedule(PauseBillingScheduleCommand command);

    /**
     * Cancels a billing schedule.
     */
    CompletionStage<BillingScheduleId> cancelBillingSchedule(CancelBillingScheduleCommand command);

    // ============ Billing Processing ============

    /**
     * Processes a single billing cycle.
     */
    CompletionStage<BillingCycleResult> processBillingCycle(ProcessBillingCycleCommand command);

    /**
     * Processes all due billing schedules.
     */
    CompletionStage<BatchBillingResult> processDueBillings(BatchBillingCommand command);

    /**
     * Retries a failed billing cycle.
     */
    CompletionStage<BillingCycleResult> retryBillingCycle(RetryBillingCycleCommand command);

    // ============ Dunning Management ============

    /**
     * Processes dunning for overdue billings.
     */
    CompletionStage<DunningResult> processDunning(ProcessDunningCommand command);

    /**
     * Handles dunning action.
     */
    CompletionStage<Void> handleDunningAction(HandleDunningActionCommand command);

    // ============ Queries ============

    /**
     * Gets billing schedule details.
     */
    CompletionStage<BillingScheduleView> getBillingSchedule(BillingScheduleId scheduleId);

    /**
     * Gets billing schedule by subscription.
     */
    CompletionStage<BillingScheduleView> getBillingScheduleBySubscription(UUID subscriptionId);

    /**
     * Gets billing history for a customer.
     */
    CompletionStage<BillingHistoryView> getBillingHistory(String customerId);

    /**
     * Gets upcoming billings.
     */
    CompletionStage<UpcomingBillingsView> getUpcomingBillings(UpcomingBillingsQuery query);

    /**
     * Gets billing statistics.
     */
    CompletionStage<BillingStatistics> getBillingStatistics(BillingStatisticsQuery query);
}