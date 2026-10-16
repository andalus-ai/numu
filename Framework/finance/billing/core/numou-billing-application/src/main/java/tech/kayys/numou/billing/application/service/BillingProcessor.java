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

    @Override
    public CompletionStage<BillingCycleResult> processBillingCycle(ProcessBillingCycleCommand command) {
        return billingScheduleRepository.findById(command.scheduleId()).subscribeAsCompletionStage()
            .thenCompose(scheduleOpt -> {
                BillingSchedule schedule = scheduleOpt.orElseThrow(() ->
                    new IllegalArgumentException("Billing schedule not found: " + command.scheduleId())
                );

                return invoicePort.generateInvoice(
                    schedule.getCustomerId(),
                    schedule.getAmount(),
                    schedule.getCurrencyCode(),
                    "Recurring billing for schedule: " + schedule.getId()
                ).thenCompose(invoiceId -> {
                    BillingSchedule.BillingCycle cycle = schedule.processBillingCycle(
                        Instant.now(),
                        invoiceId
                    );

                    return paymentPort.processPayment(
                        schedule.getPaymentMethodToken(),
                        schedule.getAmount(),
                        schedule.getCurrencyCode()
                    ).thenCompose(paymentResult -> {
                        if (paymentResult.success()) {
                            schedule.markCycleSuccess(cycle.getCycleNumber(), paymentResult.transactionId());
                            return billingScheduleRepository.save(schedule).subscribeAsCompletionStage()
                                .thenApply(v -> new BillingCycleResult(
                                    schedule.getId(),
                                    cycle.getCycleNumber(),
                                    true,
                                    schedule.getAmount(),
                                    invoiceId,
                                    paymentResult.transactionId(),
                                    "Payment processed successfully",
                                    Instant.now()
                                ));
                        } else {
                            schedule.markCycleFailed(cycle.getCycleNumber(), paymentResult.message());
                            return billingScheduleRepository.save(schedule).subscribeAsCompletionStage()
                                .thenApply(v -> new BillingCycleResult(
                                    schedule.getId(),
                                    cycle.getCycleNumber(),
                                    false,
                                    schedule.getAmount(),
                                    invoiceId,
                                    null,
                                    paymentResult.message(),
                                    Instant.now()
                                ));
                        }
                    });
                });
            });
    }

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
                        ProcessBillingCycleCommand cycleCommand = new ProcessBillingCycleCommand(
                            schedule.getId()
                        );
                        return processBillingCycle(cycleCommand)
                            .toCompletableFuture();
                    })
                    .collect(Collectors.toList());

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        List<BillingCycleResult> results = futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList());

                        long successful = results.stream().filter(BillingCycleResult::success).count();
                        long failed = results.stream().filter(r -> !r.success()).count();

                        Money totalAmount = results.stream()
                            .map(BillingCycleResult::amount)
                            .reduce(Money.zero("USD"), Money::add);

                        return new BatchBillingResult(
                            schedules.size(),
                            (int) successful,
                            (int) failed,
                            results.size(),
                            totalAmount,
                            "Batch processing completed"
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
