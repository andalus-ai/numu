package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.application.WorkspacePorts.*;
import tech.kayys.numou.workspace.domain.Page;
import tech.kayys.numou.workspace.domain.PageBlock;
import tech.kayys.numou.workspace.domain.PageTemplate;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Authorized page-template use cases. Persistence is supplied through a tenant-scoped port. */
public final class PageTemplateService {
    private final WorkspaceRepository workspaces;
    private final PageTemplateRepository templates;
    private final WorkspaceService pages;
    private final AccessPolicy access;
    private final AuditSink audit;
    private final Clock clock;
    private final boolean allowNonAtomicAuditFallback;

    public PageTemplateService(WorkspaceRepository workspaces, PageTemplateRepository templates,
                               WorkspaceService pages, AccessPolicy access, AuditSink audit, Clock clock) {
        this(workspaces, templates, pages, access, audit, clock, false);
    }

    /** Enable the non-atomic compatibility path only while migrating a legacy adapter. */
    public PageTemplateService(WorkspaceRepository workspaces, PageTemplateRepository templates,
                               WorkspaceService pages, AccessPolicy access, AuditSink audit, Clock clock,
                               boolean allowNonAtomicAuditFallback) {
        this.workspaces = Objects.requireNonNull(workspaces);
        this.templates = Objects.requireNonNull(templates);
        this.pages = Objects.requireNonNull(pages);
        this.access = Objects.requireNonNull(access);
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
        this.allowNonAtomicAuditFallback = allowNonAtomicAuditFallback;
    }

    public PageTemplate create(UUID tenantId, UUID actorId, UUID workspaceId, String name,
                               String title, List<PageBlock> blocks) {
        identity(tenantId, actorId);
        workspaces.findWorkspace(tenantId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found for tenant"));
        access.require(tenantId, actorId, Resource.WORKSPACE, workspaceId, Action.UPDATE);
        PageTemplate template = PageTemplate.create(tenantId, workspaceId, actorId, name, title, blocks, clock.instant());
        Instant now = clock.instant();
        AuditEntry event = new AuditEntry(tenantId, actorId, "PAGE_TEMPLATE_CREATED", "PAGE_TEMPLATE",
                template.id(), 1, template.name(), now);
        boolean saved;
        if (templates instanceof AuditedMutationRepository tx) saved = tx.saveTemplate(template, event);
        else if (allowNonAtomicAuditFallback) { saved = templates.save(template); if (saved) audit.record(event); }
        else throw new IllegalStateException("Atomic audited template writes require AuditedMutationRepository");
        if (!saved) throw new IllegalStateException("Template name already exists or template could not be saved");
        return template;
    }

    public List<PageTemplate> list(UUID tenantId, UUID actorId, UUID workspaceId) {
        identity(tenantId, actorId);
        workspaces.findWorkspace(tenantId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found for tenant"));
        access.require(tenantId, actorId, Resource.WORKSPACE, workspaceId, Action.READ);
        return templates.findByWorkspace(tenantId, workspaceId);
    }

    public Page createPage(UUID tenantId, UUID actorId, UUID templateId, UUID spaceId,
                           UUID parentPageId, String titleOverride) {
        identity(tenantId, actorId);
        PageTemplate template = templates.findTemplate(tenantId, templateId)
                .orElseThrow(() -> new IllegalArgumentException("Page template not found for tenant"));
        access.require(tenantId, actorId, Resource.WORKSPACE, template.workspaceId(), Action.READ);
        String title = titleOverride == null || titleOverride.isBlank() ? template.title() : titleOverride.trim();
        List<PageBlock> freshBlocks = template.instantiateBlocks();
        Page page = pages.createPage(tenantId, actorId, template.workspaceId(), spaceId, parentPageId, title, freshBlocks);
        return page;
    }

    private static void identity(UUID tenantId, UUID actorId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorId, "actorId");
    }
}
