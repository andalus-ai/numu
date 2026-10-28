package tech.kayys.numou.construction.application.consultancy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChangeConsultancyEngagementCommandTest {
    private final ConsultancyTenantContext context =
            new ConsultancyTenantContext(" tenant-a ", UUID.randomUUID());

    @Test
    void trims_idempotency_key_and_required_revision_fields() {
        var command = new ChangeConsultancyEngagementCommand(
                context, " key-1 ", UUID.randomUUID(),
                ChangeConsultancyEngagementCommand.Action.REVISE_PROPOSAL,
                " title ", " scope ", null);

        assertEquals("key-1", command.idempotencyKey());
        assertEquals("title", command.title());
        assertEquals("scope", command.scopeStatement());
        assertEquals("tenant-a", command.context().tenantId());
    }

    @Test
    void cancellation_requires_a_reason() {
        assertThrows(IllegalArgumentException.class, () -> new ChangeConsultancyEngagementCommand(
                context, "key", UUID.randomUUID(),
                ChangeConsultancyEngagementCommand.Action.CANCEL,
                null, null, " "));
    }

    @Test
    void revision_requires_both_title_and_scope() {
        assertThrows(IllegalArgumentException.class, () -> new ChangeConsultancyEngagementCommand(
                context, "key", UUID.randomUUID(),
                ChangeConsultancyEngagementCommand.Action.REVISE_PROPOSAL,
                "Title", null, null));
    }

    @Test
    void requires_trusted_context_and_idempotency_key() {
        assertThrows(NullPointerException.class, () -> new ChangeConsultancyEngagementCommand(
                null, "key", UUID.randomUUID(),
                ChangeConsultancyEngagementCommand.Action.PROPOSE, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ChangeConsultancyEngagementCommand(
                context, " ", UUID.randomUUID(),
                ChangeConsultancyEngagementCommand.Action.PROPOSE, null, null, null));
    }
}
