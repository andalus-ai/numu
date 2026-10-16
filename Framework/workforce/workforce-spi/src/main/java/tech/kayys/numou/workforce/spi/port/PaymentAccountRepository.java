package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.paymentaccount.PaymentAccount;
import tech.kayys.numou.workforce.domain.paymentaccount.PaymentAccountId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface PaymentAccountRepository extends Repository<PaymentAccount, PaymentAccountId> {

    CompletionStage<List<PaymentAccount>> findByWorker(WorkerId workerId);
}
