package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.domain.Page;
import tech.kayys.numou.workspace.domain.PageLink;
import tech.kayys.numou.workspace.domain.PageSearchHit;
import tech.kayys.numou.workspace.domain.PageTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Transport-neutral boundary for workspace content capabilities added after the base page API.
 * The host supplies identity from its authenticated request context; callers cannot choose tenant
 * or actor identifiers through request DTOs.
 */
public final class WorkspaceContentApi {
    private final WorkspaceApi.IdentityProvider identity;
    private final PageTemplateService templates;
    private final PageLinkService links;
    private final PageSearchService search;

    public WorkspaceContentApi(WorkspaceApi.IdentityProvider identity,
                               PageTemplateService templates,
                               PageLinkService links,
                               PageSearchService search) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.templates = Objects.requireNonNull(templates, "templates");
        this.links = Objects.requireNonNull(links, "links");
        this.search = Objects.requireNonNull(search, "search");
    }

    public TemplateView createTemplate(UUID workspaceId, CreateTemplateRequest request) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(request, "request");
        var actor = currentActor();
        return call(() -> TemplateView.from(templates.create(actor.tenantId(), actor.actorId(), workspaceId,
                request.name(), request.title(), blocks(request.blocks()))));
    }

    public List<TemplateView> listTemplates(UUID workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        var actor = currentActor();
        return call(() -> templates.list(actor.tenantId(), actor.actorId(), workspaceId)
                .stream().map(TemplateView::from).toList());
    }

    public WorkspaceApi.PageView createPageFromTemplate(UUID templateId, CreateFromTemplateRequest request) {
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(request, "request");
        var actor = currentActor();
        return call(() -> WorkspaceApi.PageView.from(templates.createPage(actor.tenantId(), actor.actorId(),
                templateId, request.spaceId(), request.parentPageId(), request.titleOverride())));
    }

    public List<SearchHitView> searchPages(UUID workspaceId, SearchRequest request) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(request, "request");
        var actor = currentActor();
        return call(() -> search.search(actor.tenantId(), actor.actorId(), workspaceId,
                request.spaceId(), request.query(), request.limit()).stream().map(SearchHitView::from).toList());
    }

    public LinkView createLink(UUID sourcePageId, CreateLinkRequest request) {
        Objects.requireNonNull(sourcePageId, "sourcePageId");
        Objects.requireNonNull(request, "request");
        var actor = currentActor();
        return call(() -> LinkView.from(links.link(actor.tenantId(), actor.actorId(),
                sourcePageId, request.targetPageId(), request.label())));
    }

    public void deleteLink(UUID linkId) {
        Objects.requireNonNull(linkId, "linkId");
        var actor = currentActor();
        call(() -> { links.unlink(actor.tenantId(), actor.actorId(), linkId); return null; });
    }

    public List<LinkView> outgoingLinks(UUID pageId) {
        Objects.requireNonNull(pageId, "pageId");
        var actor = currentActor();
        return call(() -> links.outgoing(actor.tenantId(), actor.actorId(), pageId)
                .stream().map(LinkView::from).toList());
    }

    public List<BacklinkView> backlinks(UUID pageId) {
        Objects.requireNonNull(pageId, "pageId");
        var actor = currentActor();
        return call(() -> links.backlinks(actor.tenantId(), actor.actorId(), pageId)
                .stream().map(BacklinkView::from).toList());
    }

    private WorkspaceApi.AuthenticatedActor currentActor() {
        WorkspaceApi.AuthenticatedActor actor = identity.currentActor();
        if (actor == null || actor.tenantId() == null || actor.actorId() == null) {
            throw new WorkspaceApi.ApiException(401, "An authenticated tenant and actor are required");
        }
        return actor;
    }

    private static List<tech.kayys.numou.workspace.domain.PageBlock> blocks(
            List<WorkspaceApi.BlockRequest> requests) {
        return requests == null ? List.of() : requests.stream().map(WorkspaceApi.BlockRequest::toDomain).toList();
    }

    private static <T> T call(Operation<T> operation) {
        try {
            return operation.run();
        } catch (WorkspaceService.RevisionConflictException conflict) {
            throw new WorkspaceApi.ApiException(409, conflict.getMessage(), conflict);
        } catch (SecurityException denied) {
            throw new WorkspaceApi.ApiException(403, "Access denied", denied);
        } catch (IllegalArgumentException invalid) {
            throw new WorkspaceApi.ApiException(400, invalid.getMessage(), invalid);
    }

    @FunctionalInterface
    private interface Operation<T> { T run(); }

    public record CreateTemplateRequest(String name, String title, List<WorkspaceApi.BlockRequest> blocks) {}
    public record CreateFromTemplateRequest(UUID spaceId, UUID parentPageId, String titleOverride) {}
    public record SearchRequest(UUID spaceId, String query, int limit) {}
    public record CreateLinkRequest(UUID targetPageId, String label) {}

    public record TemplateView(UUID id, UUID workspaceId, String name, String title,
                               List<WorkspaceApi.BlockView> blocks, Instant createdAt, Instant updatedAt) {
        public TemplateView { blocks = List.copyOf(blocks); }
        static TemplateView from(PageTemplate template) {
            return new TemplateView(template.id(), template.workspaceId(), template.name(), template.title(),
                    template.blocks().stream().map(WorkspaceApi.BlockView::from).toList(),
                    template.createdAt(), template.updatedAt());
        }
    }

    public record SearchHitView(UUID pageId, UUID workspaceId, UUID spaceId, UUID parentPageId,
                               String title, Instant updatedAt) {
        static SearchHitView from(PageSearchHit hit) {
            return new SearchHitView(hit.pageId(), hit.workspaceId(), hit.spaceId(),
                    hit.parentPageId(), hit.title(), hit.updatedAt());
        }
    }

    public record LinkView(UUID id, UUID sourcePageId, UUID targetPageId, String label, Instant createdAt) {
        static LinkView from(PageLink link) {
            return new LinkView(link.id(), link.sourcePageId(), link.targetPageId(), link.label(), link.createdAt());
        }
    }

    public record BacklinkView(UUID pageId, UUID workspaceId, UUID spaceId, String title, Instant updatedAt) {
        static BacklinkView from(Page page) {
            return new BacklinkView(page.id(), page.workspaceId(), page.spaceId(), page.title(), page.updatedAt());
        }
    }
}
