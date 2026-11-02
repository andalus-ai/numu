package tech.kayys.numou.billing.application.service;

import tech.kayys.numou.billing.application.api.BillingService;
import tech.kayys.numou.billing.application.api.command.*;
import tech.kayys.numou.billing.application.api.query.*;
import tech.kayys.numou.billing.application.port.InvoicePort;
import tech.kayys.numou.billing.application.port.PaymentPort;
import tech.kayys.numou.billing.application.port.NotificationPort;
import tech.kayys.numou.billing.domain.identifier.BillingScheduleId;
import tech.kayys.numou.billing.domain.model.BillingSchedule;
import tech.kayys.numou.billing.domain.repository.BillingScheduleRepository;
import tech.kayys.numou.billing.domain.valueobject.BillingCycleStatus;
import tech.kayys.numou.billing.domain.valueobject.BillingStatus;
import tech.kayys.numou.billing.domain.valueobject.DunningAction;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@ApplicationScoped
public class BillingProcessor implements BillingService {

    private final BillingScheduleRepository billingScheduleRepository;
    private final InvoicePort invoicePort;
    private final PaymentPort paymentPort;
    private final NotificationPort notificationPort;

    @Inject
    public BillingProcessor(
            BillingScheduleRepository billingScheduleRepository,
            InvoicePort invoicePort,
            PaymentPort paymentPort,
            NotificationPort notificationPort) {
        this.billingScheduleRepository = billingScheduleRepository;
        this.invoicePort = invoicePort;
        this.paymentPort = paymentPort;
        this.notificationPort = notificationPort;
    }

    @Override
    public CompletionStage<BillingScheduleId> createBillingSchedule(CreateBillingScheduleCommand command) {
        BillingScheduleId id = BillingScheduleId.generate();
        BillingSchedule schedule = BillingSchedule.create(
            id,
            command.subscriptionId(),
            command.customerId(),
            command.frequency(),
            Money.of(command.amount(), command.currencyCode()),
            command.currencyCode(),
            command.startDate()
        );

        if (command.customerEmail() != null) {
            schedule.setCustomerEmail(command.customerEmail());
        }
        if (command.paymentMethodToken() != null) {
            schedule.setPaymentMethodToken(command.paymentMethodToken());
        }
        if (command.totalCycles() != null) {
            schedule.setTotalCycles(command.totalCycles());
        }
        if (command.maxFailedPayments() != null) {
            schedule.setMaxFailedPayments(command.maxFailedPayments());
        }
        schedule.setSendEmailNotifications(command.sendEmailNotifications());
        schedule.setSendSmsNotifications(command.sendSmsNotifications());

        return billingScheduleRepository.save(schedule).subscribeAsCompletionStage()
            .thenApply(BillingSchedule::getId);
    }

    @Override
    public CompletionStage<BillingScheduleId> activateBillingSchedule(ActivateBillingScheduleCommand command) {
        return billingScheduleRepository.findById(command.scheduleId()).subscribeAsCompletionStage()
            .thenCompose(scheduleOpt -> {
                BillingSchedule schedule = scheduleOpt.orElseThrow(() ->
                    new IllegalArgumentException("Billing schedule not found: " + command.scheduleId())
                );
                schedule.activate();
                return billingScheduleRepository.save(schedule).subscribeAsCompletionStage()
                    .thenApply(BillingSchedule::getId);
            });
    }

    @Override
    public CompletionStage<BillingScheduleId> pauseBillingSchedule(PauseBillingScheduleCommand command) {
        return billingScheduleRepository.findById(command.scheduleId()).subscribeAsCompletionStage()
            .thenCompose(scheduleOpt -> {
                BillingSchedule schedule = scheduleOpt.orElseThrow(() ->
                    new IllegalArgumentException("Billing schedule not found: " + command.scheduleId())
                );
                schedule.pause();
                return billingScheduleRepository.save(schedule).subscribeAsCompletionStage()
                    .thenApply(BillingSchedule::getId);
            });
    }

    @Override
    public CompletionStage<BillingScheduleId> cancelBillingSchedule(CancelBillingScheduleCommand command) {
        return billingScheduleRepository.findById(command.scheduleId()).subscribeAsCompletionStage()
            .thenCompose(scheduleOpt -> {
                BillingSchedule schedule = scheduleOpt.orElseThrow(() ->
                    new IllegalArgumentException("Billing schedule not found: " + command.scheduleId())
                );
                schedule.cancel(command.reason());
                return billingScheduleRepository.save(schedule).subscribeAsCompletionStage()
                    .thenApply(BillingSchedule::getId);
            });
    }

    /**
     * Bills one cycle. The order protects the customer and the books: the schedule is checked before anything is
     * created, the cycle is stored as PENDING before any money is taken (so a crash after the charge leaves a
     * pending cycle to investigate rather than an unrecorded payment, and the next billing date has already moved
     * on so a second run cannot bill the same period), and a payment that throws counts as a failed payment.
     */
    @Override
    public CompletionStage<BillingCycleResult> processBillingCycle(ProcessBillingCycleCommand command) {
        return billingScheduleRepository.findById(command.scheduleId()).subscribeAsCompletionStage()
            .thenCompose(scheduleOpt -> {
                BillingSchedule schedule = scheduleOpt.orElseThrow(() ->
                    new IllegalArgumentException("Billing schedule not found: " + command.scheduleId())
                );
                Instant now = Instant.now();
                schedule.ensureBillable(now);

                return invoicePort.generateInvoice(
                    schedule.getCustomerId(),
                    schedule.getAmount(),
                    schedule.getCurrencyCode(),
                    "Recurring billing for schedule: " + schedule.getId()
                ).thenCompose(invoiceId -> {
                    BillingSchedule.BillingCycle cycle = schedule.processBillingCycle(now, invoiceId);

                    return billingScheduleRepository.save(schedule).subscribeAsCompletionStage()
                        .thenCompose(saved -> charge(schedule))
                        .thenCompose(paymentResult -> {
                            if (paymentResult.success()) {
                                schedule.markCycleSuccess(cycle.getCycleNumber(), paymentResult.transactionId());
                            } else {
                                schedule.markCycleFailed(cycle.getCycleNumber(), paymentResult.message());
                            }
                            return billingScheduleRepository.save(schedule).subscribeAsCompletionStage()
                                .thenApply(v -> new BillingCycleResult(
                                    schedule.getId(),
                                    cycle.getCycleNumber(),
                                    paymentResult.success(),
                                    schedule.getAmount(),
                                    invoiceId,
                                    paymentResult.success() ? paymentResult.transactionId() : null,
                                    paymentResult.success() ? "Payment processed successfully"
                                        : paymentResult.message(),
                                    Instant.now()
                                ));
                        });
                });
            });
    }

    private CompletionStage<PaymentPort.PaymentResult> charge(BillingSchedule schedule) {
        try {
            return paymentPort.processPayment(schedule.getPaymentMethodToken(), schedule.getAmount(),
                    schedule.getCurrencyCode())
                .handle((result, error) -> {
                    if (error != null) {
                        return new PaymentPort.PaymentResult(false, null,
                            "Payment could not be completed: " + rootMessage(error));
                    }
                    return result;
                });
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(
                new PaymentPort.PaymentResult(false, null, "Payment could not be completed: " + rootMessage(e)));
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while ((root instanceof java.util.concurrent.CompletionException
                || root instanceof java.util.concurrent.ExecutionException) && root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }

    /**
     * Bills every due schedule. One schedule failing (not found, not billable, a port that throws) is reported as a
     * failed result for that schedule and never stops the others. The total sums the successful charges and is only
     * given when they share one currency.
     */
    @Override
    public CompletionStage<BatchBillingResult> processDueBillings(BatchBillingCommand command) {
        return billingScheduleRepository.findDueSchedules().subscribeAsCompletionStage()
            .thenCompose(schedules -> {
                if (schedules.isEmpty()) {
                    return CompletableFuture.completedFuture(
                        new BatchBillingResult(0, 0, 0, 0, Money.zero("USD"), "No due schedules")
                    );
                }

                List<CompletableFuture<BillingCycleResult>> futures = schedules.stream()
                    .map(schedule -> {
                        CompletionStage<BillingCycleResult> stage;
                        try {
                            stage = processBillingCycle(new ProcessBillingCycleCommand(schedule.getId()));
                        } catch (RuntimeException e) {
                            stage = CompletableFuture.failedFuture(e);
                        }
                        return stage.handle((result, error) -> error == null ? result
                            : new BillingCycleResult(schedule.getId(), 0, false, schedule.getAmount(), null, null,
                                rootMessage(error), Instant.now())).toCompletableFuture();
                    })
                    .collect(Collectors.toList());

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        List<BillingCycleResult> results = futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList());

                        long successful = results.stream().filter(BillingCycleResult::success).count();
                        long failed = results.size() - successful;

                        List<Money> charged = results.stream().filter(BillingCycleResult::success)
                            .map(BillingCycleResult::amount).collect(Collectors.toList());
                        long currencies = charged.stream().map(m -> m.getCurrency().getCurrencyCode()).distinct()
                            .count();
                        Money totalAmount;
                        String message = "Batch processing completed";
                        if (currencies <= 1) {
                            totalAmount = charged.stream().reduce(Money::add).orElse(Money.zero(
                                schedules.get(0).getCurrencyCode()));
                        } else {
                            totalAmount = Money.zero(charged.get(0).getCurrency().getCurrencyCode());
                            message = "Batch processing completed; the charges are in several currencies, "
                                + "so no single total is given";
                        }

                        return new BatchBillingResult(
                            schedules.size(),
                            (int) successful,
                            (int) failed,
                            results.size(),
                            totalAmount,
                            message
                        );
                    });
            });
    }

    @Override
    public CompletionStage<BillingCycleResult> retryBillingCycle(RetryBillingCycleCommand command) {
        return CompletableFuture.completedFuture(
            new BillingCycleResult(
                command.scheduleId(),
                0,
                false,
                Money.zero("USD"),
                null,
                null,
                "Retry executed",
                Instant.now()
            )
        );
    }

    @Override
    public CompletionStage<DunningResult> processDunning(ProcessDunningCommand command) {
        return billingScheduleRepository.findSchedulesWithPaymentFailures().subscribeAsCompletionStage()
            .thenCompose(schedules -> CompletableFuture.completedFuture(
                new DunningResult(schedules.size(), 0, 0, "Dunning processed")
            ));
    }

    @Override
    public CompletionStage<Void> handleDunningAction(HandleDunningActionCommand command) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<BillingScheduleView> getBillingSchedule(BillingScheduleId scheduleId) {
        return billingScheduleRepository.findById(scheduleId).subscribeAsCompletionStage()
            .thenApply(scheduleOpt -> scheduleOpt
                .map(BillingScheduleView::fromDomain)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Billing schedule not found: " + scheduleId
                ))
            );
    }

    @Override
    public CompletionStage<BillingScheduleView> getBillingScheduleBySubscription(UUID subscriptionId) {
        return billingScheduleRepository.findBySubscriptionId(subscriptionId).subscribeAsCompletionStage()
            .thenApply(scheduleOpt -> scheduleOpt
                .map(BillingScheduleView::fromDomain)
                .orElse(null)
            );
    }

    @Override
    public CompletionStage<BillingHistoryView> getBillingHistory(String customerId) {
        return billingScheduleRepository.findByCustomerId(customerId).subscribeAsCompletionStage()
            .thenApply(schedules -> {
                List<BillingScheduleView> views = schedules.stream()
                    .map(BillingScheduleView::fromDomain)
                    .collect(Collectors.toList());
                return new BillingHistoryView(customerId, views);
            });
    }

    @Override
    public CompletionStage<UpcomingBillingsView> getUpcomingBillings(UpcomingBillingsQuery query) {
        return billingScheduleRepository.findUpcomingBilling(query.daysAhead()).subscribeAsCompletionStage()
            .thenApply(schedules -> {
                List<BillingScheduleView> views = schedules.stream()
                    .map(BillingScheduleView::fromDomain)
                    .collect(Collectors.toList());
                return new UpcomingBillingsView(views, query.daysAhead());
            });
    }

    @Override
    public CompletionStage<BillingStatistics> getBillingStatistics(BillingStatisticsQuery query) {
        return CompletableFuture.completedFuture(new BillingStatistics(
            query.fromDate(),
            query.toDate(),
            0,
            Money.zero("USD"),
            Money.zero("USD"),
            0,
            1.0,
            Money.zero("USD")
        ));
    }
}
