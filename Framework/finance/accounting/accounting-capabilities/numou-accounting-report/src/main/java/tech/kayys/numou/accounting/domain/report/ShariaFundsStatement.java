package tech.kayys.syirkah.accounting.domain.report;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

/**
 * Statement of Sources and Uses of Zakat & Benevolent Funds (PSAK 101 & 109).
 * Laporan Sumber dan Penyaluran Dana Zakat, Infaq, Shadaqah (ZIS) serta Dana Kebajikan (Qardhul Hasan).
 */
public record ShariaFundsStatement(
        ReportPeriod period,
        Money zakatInflows,
        Money zakatDisbursements,
        Money zakatBalance,
        Money infaqInflows,
        Money infaqDisbursements,
        Money infaqBalance,
        Money qardhInflows,
        Money qardhDisbursements,
        Money qardhBalance,
        Money totalDanaKebajikan
) implements ValueObject {

    public static ShariaFundsStatement of(
            ReportPeriod period,
            Money zakatIn, Money zakatOut,
            Money infaqIn, Money infaqOut,
            Money qardhIn, Money qardhOut
    ) {
        Money zakatBal = zakatIn.subtract(zakatOut);
        Money infaqBal = infaqIn.subtract(infaqOut);
        Money qardhBal = qardhIn.subtract(qardhOut);
        Money total = zakatBal.add(infaqBal).add(qardhBal);

        return new ShariaFundsStatement(
                period,
                zakatIn, zakatOut, zakatBal,
                infaqIn, infaqOut, infaqBal,
                qardhIn, qardhOut, qardhBal,
                total
        );
    }
}
