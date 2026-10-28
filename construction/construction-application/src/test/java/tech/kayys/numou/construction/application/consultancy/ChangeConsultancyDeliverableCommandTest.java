package tech.kayys.numou.construction.application.consultancy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChangeConsultancyDeliverableCommandTest {
    private final ConsultancyTenantContext context =
            new ConsultancyTenantContext(" tenant-a ", UUID.randomUUID());

    @Test
    void trims_key_and_revision_fields() {
        var command = new ChangeConsultancyDeliverableCommand(context, " key ", UUID.randomUUID(),
                ChangeConsultancyDeliverableCommand.Action.REVISE,
                " Report ", " Description ", null);
        assertEquals("key", command.idempotencyKey());
        assertEquals("Report", command.title());
        assertEquals("Description", command.description());
        assertEquals("tenant-a", command.context().tenantId());
    }

    @Test
    void rejection_requires_a_reason() {
        assertThrows(IllegalArgumentException.class, () -> new ChangeConsultancyDeliverableCommand(
                context, "key", UUID.randomUUID(), ChangeConsultancyDeliverableCommand.Action.REJECT,
                null, null, " "));
    }

    @Test
    void revision_requires_title_and_description() {
        assertThrows(IllegalArgumentException.class, () -> new ChangeConsultancyDeliverableCommand(
                context, "key", UUID.randomUUID(), ChangeConsultancyDeliverableCommand.Action.REVISE,
                "Report", null, null));
    }

    @Test
    void context_and_idempotency_key_are_required() {
        assertThrows(NullPointerException.class, () -> new ChangeConsultancyDeliverableCommand(
                null, "key", UUID.randomUUID(), ChangeConsultancyDeliverableCommand.Action.SUBMIT,
                null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ChangeConsultancyDeliverableCommand(
                context, " ", UUID.randomUUID(), ChangeConsultancyDeliverableCommand.Action.SUBMIT,
                null, null, null));
    }
}
