package tech.kayys.numou.history.graph.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOperationalStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOperationalStore.OperationalSnapshot;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalProjectionObservabilityServiceTest {
    private static final UUID TENANT=UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW=Instant.parse("2026-11-10T00:00:00Z");

    @Test void collectsAndPublishesTenantScopedSnapshot() {
        OperationalSnapshot expected=new OperationalSnapshot(TENANT,4,2,1,30,90,1,12,300,NOW);
        AtomicReference<OperationalSnapshot> emitted=new AtomicReference<>();
        HistoricalProjectionOperationalStore store=(tenant,now)->{
            assertEquals(TENANT,tenant); assertEquals(NOW,now); return expected;
        };
        var service=new HistoricalProjectionObservabilityService(store,()->TENANT,allowAll(),
                emitted::set,Clock.fixed(NOW,ZoneOffset.UTC));

        assertEquals(expected,service.collect(TENANT));
        assertEquals(expected,emitted.get());
    }

    @Test void rejectsCrossTenantBeforeStoreOrMetrics() {
        int[] calls={0};
        HistoricalProjectionOperationalStore store=(tenant,now)->{calls[0]++;return null;};
        var service=new HistoricalProjectionObservabilityService(store,()->TENANT,allowAll(),
                snapshot->calls[0]++,Clock.fixed(NOW,ZoneOffset.UTC));
        assertThrows(SecurityException.class,()->service.collect(UUID.randomUUID()));
        assertEquals(0,calls[0]);
    }

    @Test void publicCorpusRequiresAuditPermission() {
        int[] calls={0};
        HistoricalProjectionMaintenanceAuthorizationPort denied=new HistoricalProjectionMaintenanceAuthorizationPort(){
            public void requireCanRebuildTenant(UUID id){}
            public void requireCanRebuildPublicCorpus(){}
            public void requireCanAuditTenant(UUID id){}
            public void requireCanAuditPublicCorpus(){throw new SecurityException("denied");}
        };
        var service=new HistoricalProjectionObservabilityService((tenant,now)->{calls[0]++;return null;},
                ()->TENANT,denied,snapshot->calls[0]++,Clock.fixed(NOW,ZoneOffset.UTC));
        assertThrows(SecurityException.class,()->service.collect(null));
        assertEquals(0,calls[0]);
    }

    private static HistoricalProjectionMaintenanceAuthorizationPort allowAll(){
        return new HistoricalProjectionMaintenanceAuthorizationPort(){
            public void requireCanRebuildTenant(UUID id){}
            public void requireCanRebuildPublicCorpus(){}
            public void requireCanAuditTenant(UUID id){}
            public void requireCanAuditPublicCorpus(){}
        };
    }
}
