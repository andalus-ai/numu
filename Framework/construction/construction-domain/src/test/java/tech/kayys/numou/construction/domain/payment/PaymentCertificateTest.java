package tech.kayys.syirkah.construction.domain.payment;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PaymentCertificateTest {
    @Test
    void shouldCreateAndCertifyPayment() {
        var contractId = UUID.randomUUID();
        var summary = new CertificateSummary(
                BigDecimal.valueOf(100000000),
                BigDecimal.valueOf(5000000),  // 5% retention
                BigDecimal.valueOf(10000000), // advance recovery
                BigDecimal.ZERO
        );

        assertThat(summary.netPayable()).isEqualByComparingTo(BigDecimal.valueOf(85000000));

        var cert = PaymentCertificate.create(contractId, 1, summary);
        assertThat(cert.status()).isEqualTo(PaymentCertificateStatus.DRAFT);

        cert.certify();
        assertThat(cert.status()).isEqualTo(PaymentCertificateStatus.CERTIFIED);
    }
}
