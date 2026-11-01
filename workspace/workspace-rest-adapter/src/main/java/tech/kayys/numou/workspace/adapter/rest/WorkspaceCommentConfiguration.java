package tech.kayys.numou.workspace.adapter.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Instance;
import tech.kayys.numou.workspace.application.PageCommentService;
import tech.kayys.numou.workspace.application.WorkspaceApi;
import tech.kayys.numou.workspace.application.WorkspaceCommentApi;
import tech.kayys.numou.workspace.application.WorkspaceMentionService;
import tech.kayys.numou.workspace.application.WorkspacePorts.AccessPolicy;
import tech.kayys.numou.workspace.application.WorkspacePorts.AuditedPageCommentRepository;
import tech.kayys.numou.workspace.application.WorkspacePorts.PageRepository;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionDirectory;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionNotificationPort;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxStore;

import java.time.Clock;

/** CDI wiring for comments using host-provided identity, authorization, directory, and persistence ports. */
@ApplicationScoped
public class WorkspaceCommentConfiguration {

    @Produces
    @Dependent
    PageCommentService pageCommentService(PageRepository pages,
                                          AuditedPageCommentRepository comments,
                                          AccessPolicy access,
                                          Instance<WorkspaceMentionOutboxStore> mentionOutbox) {
        return new PageCommentService(pages, comments, access, Clock.systemUTC(),
                mentionOutbox.isResolvable() ? mentionOutbox.get() : null);
    }

    @Produces
    @Dependent
    WorkspaceCommentApi workspaceCommentApi(WorkspaceApi.IdentityProvider identity,
                                             PageCommentService service,
                                             PageRepository pages,
                                             AccessPolicy access,
                                             Instance<WorkspaceMentionDirectory> directory,
                                             Instance<WorkspaceMentionNotificationPort> notifications) {
        WorkspaceMentionService mentions = directory.isResolvable()
                ? new WorkspaceMentionService(pages, access, directory.get(),
                        notifications.isResolvable() ? notifications.get() : null)
                : null;
        return new WorkspaceCommentApi(identity, service, mentions);
    }
}
