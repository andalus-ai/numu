package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.application.WorkspacePorts.AccessPolicy;
import tech.kayys.numou.workspace.application.WorkspacePorts.Action;
import tech.kayys.numou.workspace.application.WorkspacePorts.PageRepository;
import tech.kayys.numou.workspace.application.WorkspacePorts.Resource;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionDirectory;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionDirectory.Member;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionNotificationPort;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionNotificationPort.MentionNotification;
import tech.kayys.numou.workspace.domain.Page;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Resolves page-comment mentions against active tenant members and emits idempotent inbox notifications. */
public final class WorkspaceMentionService {
    public static final int MAX_SUGGESTIONS = 20;
    private final PageRepository pages;
    private final AccessPolicy access;
    private final WorkspaceMentionDirectory directory;
    private final WorkspaceMentionNotificationPort notifications;

    /** Durable-outbox composition: direct post-commit Notification dispatch is not required. */
    public WorkspaceMentionService(PageRepository pages, AccessPolicy access,
                                   WorkspaceMentionDirectory directory) {
        this(pages, access, directory, null);
    }

    /**
     * Compatibility constructor for hosts that still explicitly use the legacy synchronous
     * notification method. New comment workflows should persist prepared intents via the outbox.
     */
    public WorkspaceMentionService(PageRepository pages, AccessPolicy access,
                                   WorkspaceMentionDirectory directory,
                                   WorkspaceMentionNotificationPort notifications) {
        this.pages = Objects.requireNonNull(pages, "pages");
        this.access = Objects.requireNonNull(access, "access");
        this.directory = Objects.requireNonNull(directory, "directory");
        this.notifications = notifications;
    }

    public List<Member> suggest(UUID tenantId, UUID actorId, UUID pageId, String query, int requestedLimit) {
        identity(tenantId, actorId);
        Page page = requirePage(tenantId, pageId);
        access.require(tenantId, actorId, Resource.PAGE, page.id(), Action.READ);
        String normalized = normalizeQuery(query);
        if (normalized.isEmpty()) return List.of();
        int limit = requestedLimit <= 0 ? 8 : Math.min(requestedLimit, MAX_SUGGESTIONS);
        return directory.searchActive(tenantId, normalized, limit).stream()
                .filter(Objects::nonNull)
                .filter(member -> !member.userId().equals(actorId))
                .limit(limit)
                .toList();
    }

    /** Resolves mentions before persistence so the resulting intents can be committed with the comment. */
    public List<tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxStore.MentionIntent>
    prepareCommentMentions(UUID tenantId, UUID actorId, UUID pageId, tech.kayys.numou.workspace.domain.PageComment comment) {
        identity(tenantId, actorId);
        Page page = requirePage(tenantId, pageId);
        access.require(tenantId, actorId, Resource.PAGE, page.id(), Action.UPDATE);
        if (!comment.authorId().equals(actorId) || !comment.pageId().equals(pageId) || !comment.tenantId().equals(tenantId))
            throw new IllegalArgumentException("comment identity does not match mention request");
        LinkedHashMap<UUID, Member> recipients = new LinkedHashMap<>();
        for (String handle : CommentMentionParser.parse(comment.body())) {
            directory.findActiveByHandle(tenantId, handle.toLowerCase(Locale.ROOT))
                    .filter(member -> !member.userId().equals(actorId))
                    .ifPresent(member -> recipients.putIfAbsent(member.userId(), member));
        }
        String actorName = directory.findActiveByUserId(tenantId, actorId)
                .map(Member::displayName).orElse("A teammate");
        return recipients.values().stream().map(member ->
                new tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxStore.MentionIntent(
                        member.userId(), member.handle(), actorName, page.title(),
                        "workspace:comment-mention:" + comment.id() + ":" + member.userId()))
                .toList();
    }

    /** Legacy direct-delivery path. Durable comment workflows must use prepareCommentMentions instead. */
    @Deprecated(forRemoval = false)
    public List<UUID> notifyCommentMentions(UUID tenantId, UUID actorId, UUID pageId,
                                            UUID commentId, String body) {
        if (notifications == null) {
            throw new IllegalStateException("Synchronous mention notifications are not configured; use the durable outbox");
        }
        identity(tenantId, actorId);
        Page page = requirePage(tenantId, pageId);
        access.require(tenantId, actorId, Resource.PAGE, page.id(), Action.UPDATE);
        List<String> handles = CommentMentionParser.parse(body);
        LinkedHashMap<UUID, Member> recipients = new LinkedHashMap<>();
        for (String handle : handles) {
            directory.findActiveByHandle(tenantId, handle.toLowerCase(Locale.ROOT))
                    .filter(member -> !member.userId().equals(actorId))
                    .ifPresent(member -> recipients.putIfAbsent(member.userId(), member));
        }
        String actorName = directory.findActiveByUserId(tenantId, actorId)
                .map(Member::displayName).orElse("A teammate");
        List<UUID> notified = new ArrayList<>();
        for (Member member : recipients.values()) {
            String key = "workspace:comment-mention:" + commentId + ":" + member.userId();
            notifications.notifyMention(new MentionNotification(tenantId, member.userId(), actorId,
                    pageId, commentId, member.handle(), actorName, page.title(), key));
            notified.add(member.userId());
        }
        return List.copyOf(notified);
    }

    private Page requirePage(UUID tenantId, UUID pageId) {
        return pages.findPage(tenantId, pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found for tenant"));
    }

    private static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) return "";
        String value = query.trim();
        if (value.startsWith("@")) value = value.substring(1);
        if (value.length() > 64 || !value.matches("[A-Za-z0-9._-]*"))
            throw new IllegalArgumentException("mention query contains unsupported characters");
        return value.toLowerCase(Locale.ROOT);
    }

    private static void identity(UUID tenantId, UUID actorId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(actorId, "actorId is required");
    }
}
