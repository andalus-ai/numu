package tech.kayys.syirkah.accounting.domain.legal;

import java.time.LocalDate;
import java.util.Objects;

public final class LegalContract {
    public enum Status { DRAFT, ACTIVE, EXPIRED, TERMINATED }

    private final String contractId;
    private final String title;
    private final String countsyirkaharty;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private Status status;
    private boolean autoRenew;
    private int renewalNoticePeriodDays;

    public LegalContract(String contractId, String title, String countsyirkaharty, LocalDate startDate, LocalDate endDate) {
        this.contractId = Objects.requireNonNull(contractId, "contractId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.countsyirkaharty = Objects.requireNonNull(countsyirkaharty, "countsyirkaharty must not be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate must not be null");
        this.endDate = Objects.requireNonNull(endDate, "endDate must not be null");
        this.status = Status.DRAFT;
        this.autoRenew = false;
        this.renewalNoticePeriodDays = 30;
    }

    public void activate() {
        this.status = Status.ACTIVE;
    }

    public void terminate() {
        if (this.status != Status.ACTIVE) {
            throw new IllegalStateException("Cannot terminate non-active contract: " + contractId);
        }
        this.status = Status.TERMINATED;
    }

    public void configureRenewal(boolean autoRenew, int renewalNoticePeriodDays) {
        this.autoRenew = autoRenew;
        this.renewalNoticePeriodDays = Math.max(0, renewalNoticePeriodDays);
    }

    public LocalDate noticeDeadline() {
        return endDate.minusDays(renewalNoticePeriodDays);
    }

    public boolean isRenewalNoticeDue(LocalDate today) {
        return autoRenew && status == Status.ACTIVE && !today.isBefore(noticeDeadline()) && today.isBefore(endDate);
    }

    public String contractId() { return contractId; }
    public String title() { return title; }
    public String countsyirkaharty() { return countsyirkaharty; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
    public Status status() { return status; }
    public boolean isAutoRenew() { return autoRenew; }
    public int renewalNoticePeriodDays() { return renewalNoticePeriodDays; }
}
