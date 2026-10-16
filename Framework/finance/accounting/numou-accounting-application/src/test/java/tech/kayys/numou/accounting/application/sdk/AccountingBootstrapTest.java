package tech.kayys.syirkah.accounting.application.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.sdk.bootstrap.AccountingBootstrap;
import tech.kayys.syirkah.accounting.application.sdk.bootstrap.AccountingEngine;
import tech.kayys.syirkah.accounting.application.sdk.plugin.aaoifi.AaoifiLitePlugin;
import tech.kayys.syirkah.accounting.application.sdk.testkit.AccountingTestKit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AccountingBootstrap — Embedded SDK")
class AccountingBootstrapTest {

    @Test
    @DisplayName("lite() builds a valid engine without plugins")
    void liteBuildNoPlugins() {
        AccountingEngine engine = AccountingBootstrap.lite().build();
        assertNotNull(engine);
        assertNotNull(engine.commandBus());
        assertNotNull(engine.queryBus());
        assertNotNull(engine.outboxRepository());
        assertNotNull(engine.projectionRegistry());
        assertFalse(engine.hasPlugins());
    }

    @Test
    @DisplayName("withPlugin(AaoifiLitePlugin) registers plugin and zakat projection")
    void withAaoifiLitePlugin() {
        AaoifiLitePlugin plugin = new AaoifiLitePlugin();
        AccountingEngine engine = AccountingBootstrap.lite()
                .withPlugin(plugin)
                .build();

        assertTrue(engine.hasPlugins());
        assertEquals(1, engine.pluginRegistry().all().size());
        assertEquals("AAOIFI Lite",
                engine.pluginRegistry().all().getFirst().metadata().name());

        // Zakat projection should be registered
        assertFalse(engine.projectionRegistry().all().isEmpty());
    }

    @Test
    @DisplayName("AccountingTestKit assertPluginCount works correctly")
    void testKitPluginCount() {
        AccountingTestKit kit = AccountingTestKit.create()
                .withPlugin(new AaoifiLitePlugin())
                .build();

        kit.assertPluginCount(1);
    }
}
