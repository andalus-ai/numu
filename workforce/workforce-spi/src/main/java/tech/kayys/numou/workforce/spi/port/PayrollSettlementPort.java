package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.workforce.domain.payroll.PayrollRunId;
import tech.kayys.numou.workforce.domain.payslip.PayslipId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.math.BigDecimal;
import java.util.concurrent.CompletionStage;

/**
 * SPI port for disbursing payroll funds to workers.
 * Connects workforce payroll to the finance/payment bounded context
 * without coupling the domain to payment internals.
 */
public interface PayrollSettlementPort {

    /**
     * Initiates disbursement of the net pay for a finalized payslip.
     *
     * @param runId       the payroll run that produced this payslip
     * @param payslipId   the payslip to settle
     * @param workerId    the receiving worker
     * @param netAmount   the net amount to disburse
     * @param currency    ISO-4217 currency code
     * @return an external payment reference ID (e.g. from the payment bounded context)
     */
    CompletionStage<String> disburse(
            PayrollRunId runId,
            PayslipId payslipId,
            WorkerId workerId,
            BigDecimal netAmount,
            String currency
    );

    /**
     * Checks the settlement status of a previously initiated disbursement.
     *
     * @param externalPaymentRef the reference returned by disburse()
     * @return current settlement status
     */
    CompletionStage<SettlementStatus> checkStatus(String externalPaymentRef);

    enum SettlementStatus {
        PENDING, PROCESSING, SETTLED, FAILED, RETURNED
    }
}
