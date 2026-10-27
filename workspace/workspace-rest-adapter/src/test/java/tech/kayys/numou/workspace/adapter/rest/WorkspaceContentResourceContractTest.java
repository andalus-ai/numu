package tech.kayys.numou.workspace.adapter.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.QueryParam;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WorkspaceContentResourceContractTest {
    @Test
    void requestDtosNeverAllowCallersToSelectTenantOrActor() {
        assertNoIdentityFields(WorkspaceContentResource.CreateTemplateRequest.class);
        assertNoIdentityFields(WorkspaceContentResource.CreateFromTemplateRequest.class);
        assertNoIdentityFields(WorkspaceContentResource.CreateLinkRequest.class);
        assertNoIdentityFields(WorkspaceContentResource.BlockRequest.class);
    }

    @Test
    void searchLimitIsBoundedAtTransportBoundary() throws Exception {
        var method = WorkspaceContentResource.class.getMethod("searchPages",
                java.util.UUID.class, String.class, java.util.UUID.class, int.class);
        var parameters = method.getParameters();
        assertEquals("q", parameters[1].getAnnotation(QueryParam.class).value());
        assertEquals("spaceId", parameters[2].getAnnotation(QueryParam.class).value());
        assertEquals(1, parameters[3].getAnnotation(Min.class).value());
        assertEquals(100, parameters[3].getAnnotation(Max.class).value());
    }

    private static void assertNoIdentityFields(Class<?> recordType) {
        List<String> names = Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertFalse(names.contains("tenantId"), recordType.getSimpleName());
        assertFalse(names.contains("actorId"), recordType.getSimpleName());
    }
}
