package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SpendFromWalletCommand(TenantId tenantId, SantriId santriId, BigDecimal amount, LocalDate date, String note) {}
