package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code dailyLimit} only applies when the wallet is created by this deposit; it may be null. */
public record DepositToWalletCommand(TenantId tenantId, SantriId santriId, BigDecimal amount, LocalDate date,
                                     String note, BigDecimal dailyLimit) {}
