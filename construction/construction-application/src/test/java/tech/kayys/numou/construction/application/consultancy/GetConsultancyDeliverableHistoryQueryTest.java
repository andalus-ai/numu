package tech.kayys.numou.construction.application.consultancy;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GetConsultancyDeliverableHistoryQueryTest {
    @Test
    void requiresTrustedContextAndResourceScope() {
        ConsultancyTenantContext context = new ConsultancyTenantContext("tenant-a", UUID.randomUUID());
        assertThrows(NullPointerException.class,
                () -> new GetConsultancyDeliverableHistoryQuery(null, UUID.randomUUID(), UUID.randomUUID()));
        assertThrows(NullPointerException.class,
                () -> new GetConsultancyDeliverableHistoryQuery(context, null, UUID.randomUUID()));
        assertThrows(NullPointerException.class,
                () -> new GetConsultancyDeliverableHistoryQuery(context, UUID.randomUUID(), null));
    }
}
