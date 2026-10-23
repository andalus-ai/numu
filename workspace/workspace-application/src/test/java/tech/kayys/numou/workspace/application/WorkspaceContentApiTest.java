package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.WorkspaceApi.AuthenticatedActor;
import tech.kayys.numou.workspace.application.WorkspaceContentApi.*;
import tech.kayys.numou.workspace.application.WorkspacePorts.*;
import tech.kayys.numou.workspace.domain.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceContentApiTest {
    @Test
    void rejectsMissingTrustedIdentityBeforeCallingContentServices() {
        WorkspaceContentApi api = api(() -> null);
        WorkspaceApi.ApiException error = assertThrows(WorkspaceApi.ApiException.class,
                () -> api.listTemplates(UUID.randomUUID()));
        assertEquals(401, error.status());
    }

    @Test
    void requestContractsDoNotAcceptCallerSuppliedTenantOrActorIds() {
        assertEquals(List.of("name", "title", "blocks"),
                java.util.Arrays.stream(CreateTemplateRequest.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList());
        assertEquals(List.of("spaceId", "query", "limit"),
                java.util.Arrays.stream(SearchRequest.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList());
    }

    private static WorkspaceContentApi api(WorkspaceApi.IdentityProvider identity) {
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        WorkspaceRepository workspaces = new WorkspaceRepository() {
            public boolean save(Workspace value) { return false; }
            public Optional<Workspace> findWorkspace(UUID tenant, UUID workspace) { return Optional.empty(); }
        };
        SpaceRepository spaces = new SpaceRepository() {
            public boolean save(Space value) { return false; }
            public Optional<Space> find(UUID tenant, UUID workspace, UUID space) { return Optional.empty(); }
            public boolean slugExists(UUID tenant, UUID workspace, String slug) { return false; }
        };
        PageRepository pages = new PageRepository() {
            public Optional<Page> findPage(UUID tenant, UUID page) { return Optional.empty(); }
            public List<Page> findChildren(UUID tenant, UUID space, UUID parent) { return List.of(); }
            public boolean saveWithRevision(Page page, PageRevision revision, long expectedRevision) { return false; }
            public List<PageRevision> history(UUID tenant, UUID page) { return List.of(); }
        };
        PageLinkRepository pageLinks = new PageLinkRepository() {
            public boolean save(PageLink link) { return false; }
            public Optional<PageLink> findLink(UUID tenant, UUID link) { return Optional.empty(); }
            public boolean delete(UUID tenant, UUID link) { return false; }
            public List<PageLink> outgoing(UUID tenant, UUID page) { return List.of(); }
            public List<PageLink> backlinks(UUID tenant, UUID page) { return List.of(); }
        };
        PageTemplateRepository templates = new PageTemplateRepository() {
            public boolean save(PageTemplate template) { return false; }
            public Optional<PageTemplate> findTemplate(UUID tenant, UUID template) { return Optional.empty(); }
            public List<PageTemplate> findByWorkspace(UUID tenant, UUID workspace) { return List.of(); }
        };
        AccessPolicy access = (tenant, actor, resource, id, action) -> {};
        AuditSink audit = entry -> {};
        AttachmentPolicy attachments = (tenant, actor, fileId) -> fileId;
        WorkspaceService workspaceService = new WorkspaceService(
                workspaces, spaces, pages, access, audit, attachments, clock);
        return new WorkspaceContentApi(identity,
                new PageTemplateService(workspaces, templates, workspaceService, access, audit, clock),
                new PageLinkService(pages, pageLinks, access, audit, clock),
                new PageSearchService(workspaces, spaces, (tenant, workspace, space, query, limit) -> List.of(), access));
    }
}
