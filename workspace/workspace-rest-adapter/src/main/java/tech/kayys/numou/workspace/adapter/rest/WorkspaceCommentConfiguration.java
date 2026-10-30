package tech.kayys.numou.workspace.adapter.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.workspace.application.PageCommentService;
import tech.kayys.numou.workspace.application.WorkspaceApi;
import tech.kayys.numou.workspace.application.WorkspaceCommentApi;
import tech.kayys.numou.workspace.application.WorkspacePorts.AccessPolicy;
import tech.kayys.numou.workspace.application.WorkspacePorts.AuditedPageCommentRepository;
import tech.kayys.numou.workspace.application.WorkspacePorts.PageRepository;

import java.time.Clock;

/** CDI wiring for the optional comment capability using the host's existing Workspace ports and identity. */
@ApplicationScoped
public class WorkspaceCommentConfiguration {

    @Produces
    @Dependent
    PageCommentService pageCommentService(PageRepository pages,
                                          AuditedPageCommentRepository comments,
                                          AccessPolicy access) {
        return new PageCommentService(pages, comments, access, Clock.systemUTC());
    }

    @Produces
    @Dependent
    WorkspaceCommentApi workspaceCommentApi(WorkspaceApi.IdentityProvider identity,
                                             PageCommentService service) {
        return new WorkspaceCommentApi(identity, service);
    }
}
