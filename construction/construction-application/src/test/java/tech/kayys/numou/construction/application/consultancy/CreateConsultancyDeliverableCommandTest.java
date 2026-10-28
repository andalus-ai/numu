package tech.kayys.numou.construction.application.consultancy;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CreateConsultancyDeliverableCommandTest {
    @Test
    void normalizesTextAndKeepsTrustedContext() {
        ConsultancyTenantContext context = new ConsultancyTenantContext(" tenant-a ", UUID.randomUUID());
        CreateConsultancyDeliverableCommand command = new CreateConsultancyDeliverableCommand(
                context, " key-1 ", UUID.randomUUID(), " D-01 ", " Report ", " Signed report ");
        assertEquals("tenant-a", command.context().tenantId());
        assertEquals("key-1", command.idempotencyKey());
        assertEquals("D-01", command.code());
        assertEquals("Report", command.title());
        assertEquals("Signed report", command.description());
    }

    @Test
    void requiresEngagementAndNonBlankDeliverableFields() {
        ConsultancyTenantContext context = new ConsultancyTenantContext("tenant-a", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> new CreateConsultancyDeliverableCommand(
                context, "key", UUID.randomUUID(), " ", "Report", "Description"));
        assertThrows(NullPointerException.class, () -> new CreateConsultancyDeliverableCommand(
                context, "key", null, "D-01", "Report", "Description"));
    }
}
