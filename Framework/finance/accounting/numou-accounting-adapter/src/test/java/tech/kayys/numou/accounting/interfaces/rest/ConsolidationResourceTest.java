package tech.kayys.syirkah.accounting.interfaces.rest;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.consolidation.ConsolidationGroup;
import tech.kayys.syirkah.accounting.consolidation.ConsolidationRun;
import tech.kayys.syirkah.accounting.consolidation.ConsolidationService;
import tech.kayys.syirkah.accounting.consolidation.OwnershipEngine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConsolidationResourceTest {

    private ConsolidationResource resource;

    @BeforeEach
    void setUp() {
        resource = new ConsolidationResource();
        resource.consolidationService = new ConsolidationService();
        resource.ownershipEngine = new OwnershipEngine();
    }

    @Test
    void testDefineGroupAndStartRun() {
        var groupReq = new ConsolidationResource.DefineGroupRequest(
                "GRP-01", "HOLDING", "Global Holding Group", "USD", "ENTERPRISE"
        );
        Response groupResp = resource.defineGroup(groupReq).await().indefinitely();
        assertEquals(201, groupResp.getStatus());
        ConsolidationGroup group = (ConsolidationGroup) groupResp.getEntity();
        assertEquals("GRP-01", group.groupId());

        var runReq = new ConsolidationResource.StartRunRequest(
                "TENANT-01", LocalDate.of(2026, 3, 31), "USD"
        );
        Response runResp = resource.startRun(runReq).await().indefinitely();
        assertEquals(201, runResp.getStatus());
        ConsolidationRun run = (ConsolidationRun) runResp.getEntity();
        assertEquals("USD", run.presentationCurrency());

        // Effective ownership test
        var ownReq = new ConsolidationResource.EffectiveOwnershipRequest(
                new BigDecimal[]{new BigDecimal("0.80"), new BigDecimal("0.75")}
        );
        Response ownResp = resource.calculateEffectiveOwnership(ownReq).await().indefinitely();
        assertEquals(200, ownResp.getStatus());
        var map = (Map<?, ?>) ownResp.getEntity();
        assertEquals(0, new BigDecimal("0.600000").compareTo((BigDecimal) map.get("effectiveOwnership")));
        assertEquals(0, new BigDecimal("0.400000").compareTo((BigDecimal) map.get("nciPercentage")));
    }
}
