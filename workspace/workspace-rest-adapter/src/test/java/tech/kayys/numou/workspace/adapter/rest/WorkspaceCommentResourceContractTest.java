package tech.kayys.numou.workspace.adapter.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceCommentResourceContractTest {
    @Test
    void commentRequestsDoNotAcceptTenantOrActorIdentity() {
        assertNoIdentityFields(WorkspaceCommentResource.CreateThreadRequest.class);
        assertNoIdentityFields(WorkspaceCommentResource.ReplyRequest.class);
        assertNoIdentityFields(WorkspaceCommentResource.ResolutionRequest.class);
    }

    @Test
    void endpointsArePageScopedAndExposeThreadLifecycle() throws Exception {
        assertEquals("/api/v1", WorkspaceCommentResource.class.getAnnotation(Path.class).value());
        assertEndpoint("listThreads", GET.class, "/pages/{pageId}/comment-threads");
        assertEndpoint("suggestMentions", GET.class, "/pages/{pageId}/mention-suggestions");
        assertEndpoint("createThread", POST.class, "/pages/{pageId}/comment-threads");
        assertEndpoint("reply", POST.class, "/pages/{pageId}/comment-threads/{threadId}/replies");
        assertEndpoint("resolve", POST.class, "/pages/{pageId}/comment-threads/{threadId}/resolve");
        assertEndpoint("reopen", POST.class, "/pages/{pageId}/comment-threads/{threadId}/reopen");
    }

    @Test
    void createCommentRequestAcceptsOnlyOptionalBlockAnchorInAdditionToBody() {
        var components = Arrays.stream(WorkspaceCommentResource.CreateThreadRequest.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertEquals(List.of("body", "anchorBlockId"), components);
        assertNotNull(WorkspaceCommentResource.CreateThreadRequest.class.getRecordComponents()[0]
                .getAnnotation(NotBlank.class));
    }

    @Test
    void threadResponsesExposeTheStableBlockAnchor() {
        var components = Arrays.stream(tech.kayys.numou.workspace.application.WorkspaceCommentApi.ThreadView.class
                .getRecordComponents()).map(RecordComponent::getName).toList();
        assertTrue(components.contains("anchorBlockId"));
    }

    @Test
    void requestBodiesAreValidatedAtTheTransportBoundary() {
        assertNotNull(WorkspaceCommentResource.CreateThreadRequest.class.getRecordComponents()[0]
                .getAnnotation(NotBlank.class));
        assertNotNull(WorkspaceCommentResource.ReplyRequest.class.getRecordComponents()[0]
                .getAnnotation(NotNull.class));
        assertNotNull(WorkspaceCommentResource.ReplyRequest.class.getRecordComponents()[1]
                .getAnnotation(NotBlank.class));
        assertNotNull(WorkspaceCommentResource.ResolutionRequest.class.getRecordComponents()[0]
                .getAnnotation(Positive.class));
    }

    private static void assertEndpoint(String name, Class<?> annotation, String path) throws Exception {
        var method = Arrays.stream(WorkspaceCommentResource.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
        assertNotNull(method.getAnnotation((Class) annotation));
        assertEquals(path, method.getAnnotation(Path.class).value());
    }

    private static void assertNoIdentityFields(Class<?> recordType) {
        List<String> names = Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertFalse(names.contains("tenantId"), recordType.getSimpleName());
        assertFalse(names.contains("actorId"), recordType.getSimpleName());
    }
}
