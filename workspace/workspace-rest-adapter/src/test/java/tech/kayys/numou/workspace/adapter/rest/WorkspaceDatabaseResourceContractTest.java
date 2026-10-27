package tech.kayys.numou.workspace.adapter.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WorkspaceDatabaseResourceContractTest {
    @Test
    void writeRequestsNeverAllowCallerSuppliedTenantOrActorIdentity() {
        assertNoIdentityFields(WorkspaceDatabaseResource.CreateDatabaseRequest.class);
        assertNoIdentityFields(WorkspaceDatabaseResource.CreateTableRequest.class);
        assertNoIdentityFields(WorkspaceDatabaseResource.UpdateTableSchemaRequest.class);
        assertNoIdentityFields(WorkspaceDatabaseResource.RecordValuesRequest.class);
        assertNoIdentityFields(WorkspaceDatabaseResource.UpdateRecordRequest.class);
        assertNoIdentityFields(WorkspaceDatabaseResource.ViewRequest.class);
        assertNoIdentityFields(WorkspaceDatabaseResource.UpdateViewRequest.class);
    }

    @Test
    void recordPaginationHasExplicitBoundaries() throws Exception {
        var method = WorkspaceDatabaseResource.class.getMethod("queryRecords",
                java.util.UUID.class, java.util.UUID.class, int.class, int.class);
        var parameters = method.getParameters();
        assertEquals(1, parameters[2].getAnnotation(Min.class).value());
        assertEquals(200, parameters[2].getAnnotation(Max.class).value());
        assertEquals(0, parameters[3].getAnnotation(Min.class).value());
    }

    private static void assertNoIdentityFields(Class<?> recordType) {
        List<String> names = Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertFalse(names.contains("tenantId"), recordType.getSimpleName());
        assertFalse(names.contains("actorId"), recordType.getSimpleName());
    }
}
