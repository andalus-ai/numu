package tech.kayys.numou.workspace.collaboration.quarkus;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import tech.kayys.numou.workspace.collaboration.local.LocalWorkspacePageChangeHub;

class WorkspaceCollaborationCapacityExceptionMapperTest {
    @Test void mapsConnectionQuotaRejectionToTooManyRequests() {
        var mapper = new WorkspaceCollaborationCapacityExceptionMapper();
        var response = mapper.toResponse(
                new LocalWorkspacePageChangeHub.SubscriptionLimitExceededException("limit reached"));
        assertEquals(429, response.getStatus());
        assertTrue(response.getEntity() == null);
    }
}
