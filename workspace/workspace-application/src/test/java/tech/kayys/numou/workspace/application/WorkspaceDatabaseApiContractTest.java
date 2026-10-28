package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceDatabaseApiContractTest {
    @Test
    void recordRequestsPreserveExplicitNullValuesForClearingOptionalFields() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("optionalText", null);
        values.put("title", "Updated");

        var create = new WorkspaceDatabaseApi.RecordValuesRequest(values);
        var update = new WorkspaceDatabaseApi.UpdateRecordRequest(4, values);

        assertTrue(create.values().containsKey("optionalText"));
        assertNull(create.values().get("optionalText"));
        assertTrue(update.values().containsKey("optionalText"));
        assertNull(update.values().get("optionalText"));
        assertThrows(UnsupportedOperationException.class, () -> update.values().put("other", "value"));
    }

    @Test
    void nullFilterValuesRemainRepresentableWithoutMakingRequestMapsMutable() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("optionalText", null);

        var query = new WorkspaceDatabaseApi.QueryRecordsRequest(filters, 50, 0);
        var view = new WorkspaceDatabaseApi.CreateViewRequest(
                "All records", null, filters, List.of(), List.of(), null);

        assertTrue(query.filters().containsKey("optionalText"));
        assertNull(query.filters().get("optionalText"));
        assertTrue(view.filters().containsKey("optionalText"));
        assertNull(view.filters().get("optionalText"));
        assertThrows(UnsupportedOperationException.class, () -> query.filters().put("other", "value"));
    }

    @Test
    void requestContractsNeverAcceptCallerSuppliedTenantOrActorIdentity() {
        List<Class<?>> requestTypes = List.of(
                WorkspaceDatabaseApi.CreateDatabaseRequest.class,
                WorkspaceDatabaseApi.CreateTableRequest.class,
                WorkspaceDatabaseApi.UpdateTableSchemaRequest.class,
                WorkspaceDatabaseApi.RecordValuesRequest.class,
                WorkspaceDatabaseApi.UpdateRecordRequest.class,
                WorkspaceDatabaseApi.QueryRecordsRequest.class,
                WorkspaceDatabaseApi.CreateViewRequest.class,
                WorkspaceDatabaseApi.UpdateViewRequest.class);

        for (Class<?> requestType : requestTypes) {
            List<String> components = Arrays.stream(requestType.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName).toList();
            assertFalse(components.contains("tenantId"), requestType.getSimpleName());
            assertFalse(components.contains("actorId"), requestType.getSimpleName());
        }
    }
}
