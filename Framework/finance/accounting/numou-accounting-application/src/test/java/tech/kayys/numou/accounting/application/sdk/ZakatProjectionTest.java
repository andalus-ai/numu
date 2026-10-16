package tech.kayys.syirkah.accounting.application.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.sdk.bootstrap.AccountingBootstrap;
import tech.kayys.syirkah.accounting.application.sdk.bootstrap.AccountingEngine;
import tech.kayys.syirkah.accounting.application.sdk.plugin.aaoifi.AaoifiLitePlugin;
import tech.kayys.syirkah.accounting.domain.event.MurabahaCreated;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("ZakatProjection — AAOIFI FAS 9 Simplified")
class ZakatProjectionTest {

    private static final TenantRef TENANT = new TenantRef("tenant-acme");
    private static final LedgerId LEDGER = new LedgerId("SHARIAH");

    @Test
    @DisplayName("zakatObligation is 2.5% of total Murabahah portfolio")
    void zakatObligation() {
        AaoifiLitePlugin plugin = new AaoifiLitePlugin();
        AccountingEngine engine = AccountingBootstrap.lite()
                .withPlugin(plugin)
                .build();

        MurabahaCreated e1 = MurabahaCreated.of(
                TENANT, LEDGER, "MCT-001",
                new BigDecimal("10000.00"), new BigDecimal("1200.00"),
                new BigDecimal("11200.00"), 12, "MCT-001", null);

        MurabahaCreated e2 = MurabahaCreated.of(
                TENANT, LEDGER, "MCT-002",
                new BigDecimal("20000.00"), new BigDecimal("2400.00"),
                new BigDecimal("22400.00"), 24, "MCT-002", null);

        engine.projectionRegistry().publishToProjections(e1).await().indefinitely();
        engine.projectionRegistry().publishToProjections(e2).await().indefinitely();

        // portfolio = 11200 + 22400 = 33600; zakat = 33600 * 0.025 = 840.00
        BigDecimal zakat = plugin.zakatProjection().zakatObligation(TENANT.value());
        assertEquals(0, new BigDecimal("840.00").compareTo(zakat));
    }
}
