const DEFAULT_API_BASE = "/api/v1";

export class WorkspaceApiError extends Error {
  constructor(message, { status = 0, code = "NETWORK_ERROR", details = null } = {}) {
    super(message);
    this.name = "WorkspaceApiError";
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export function createWorkspaceApi({
  baseUrl = DEFAULT_API_BASE,
  fetchImpl = globalThis.fetch,
} = {}) {
  if (typeof fetchImpl !== "function") {
    throw new TypeError("A fetch implementation is required");
  }

  const base = String(baseUrl).replace(/\/+$/, "");

  async function request(path, { method = "GET", body, signal } = {}) {
    let response;
    try {
      response = await fetchImpl(`${base}${path}`, {
        method,
        credentials: "same-origin",
        headers: {
          Accept: "application/json",
          ...(body === undefined ? {} : { "Content-Type": "application/json" }),
        },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
        ...(signal ? { signal } : {}),
      });
    } catch (error) {
      if (error?.name === "AbortError") throw error;
      throw new WorkspaceApiError("The workspace service could not be reached.", {
        code: "NETWORK_ERROR",
      });
    }

    if (response.status === 204) return null;
    const contentType = response.headers?.get?.("content-type") ?? "";
    let payload = null;
    if (contentType.includes("application/json")) {
      try {
        payload = await response.json();
      } catch {
        payload = null;
      }
    }

    if (!response.ok) {
      const status = response.status;
      const message = status === 401
        ? "Your session has expired. Sign in and try again."
        : status === 403 || status === 404
          ? "This workspace resource is unavailable or you do not have access."
          : status === 409
            ? "This item changed elsewhere. Your edits have not been discarded."
            : status >= 500
              ? "The workspace service encountered an error. Try again later."
              : "The request could not be completed. Check the fields and try again.";
      throw new WorkspaceApiError(message, {
        status,
        code: status === 409 ? "REVISION_CONFLICT" : "HTTP_ERROR",
        details: payload,
      });
    }
    return payload;
  }

  const json = (method, path, body, options = {}) =>
    request(path, { ...options, method, body });

  return Object.freeze({
    listWorkspaces: () => request("/workspaces"),
    listSpaces: (workspaceId) => request("/workspaces/" + encodeURIComponent(workspaceId) + "/spaces"),
    createWorkspace: (input) => json("POST", "/workspaces", input),
    createSpace: (workspaceId, input) =>
      json("POST", `/workspaces/${encodeURIComponent(workspaceId)}/spaces`, input),
    createPage: (workspaceId, spaceId, input) =>
      json("POST", `/workspaces/${encodeURIComponent(workspaceId)}/spaces/${encodeURIComponent(spaceId)}/pages`, input),
    getPage: (pageId, options) =>
      request(`/pages/${encodeURIComponent(pageId)}`, options),
    updatePage: (pageId, input) =>
      json("PUT", `/pages/${encodeURIComponent(pageId)}`, input),
    listPages: (spaceId, parentPageId) => {
      const query = new URLSearchParams();
      if (parentPageId) query.set("parentPageId", parentPageId);
      const suffix = query.size ? `?${query.toString()}` : "";
      return request(`/spaces/${encodeURIComponent(spaceId)}/pages${suffix}`);
    },
    getPageHistory: (pageId) =>
      request(`/pages/${encodeURIComponent(pageId)}/history`),
    restorePageRevision: (pageId, input) =>
      json("POST", `/pages/${encodeURIComponent(pageId)}/history/restore`, input),
    searchPages: (workspaceId, query, spaceId, limit = 20) => {
      const params = new URLSearchParams({ q: query, limit: String(limit) });
      if (spaceId) params.set("spaceId", spaceId);
      return request(`/workspaces/${encodeURIComponent(workspaceId)}/search?${params.toString()}`);
    },
    listTemplates: (workspaceId) =>
      request(`/workspaces/${encodeURIComponent(workspaceId)}/templates`),
    createPageFromTemplate: (templateId, input) =>
      json("POST", `/templates/${encodeURIComponent(templateId)}/pages`, input),
    getOutgoingLinks: (pageId) =>
      request(`/pages/${encodeURIComponent(pageId)}/links`),
    getBacklinks: (pageId) =>
      request(`/pages/${encodeURIComponent(pageId)}/backlinks`),
    suggestMentions: (pageId, query, limit = 8) => {
      const params = new URLSearchParams({ q: query, limit: String(limit) });
      return request(`/pages/${encodeURIComponent(pageId)}/mention-suggestions?${params.toString()}`);
    },
    listCommentThreads: (pageId) =>
      request(`/pages/${encodeURIComponent(pageId)}/comment-threads`),
    createCommentThread: (pageId, input) =>
      json("POST", `/pages/${encodeURIComponent(pageId)}/comment-threads`, input),
    replyToCommentThread: (pageId, threadId, input) =>
      json("POST", `/pages/${encodeURIComponent(pageId)}/comment-threads/${encodeURIComponent(threadId)}/replies`, input),
    resolveCommentThread: (pageId, threadId, input) =>
      json("POST", `/pages/${encodeURIComponent(pageId)}/comment-threads/${encodeURIComponent(threadId)}/resolve`, input),
    reopenCommentThread: (pageId, threadId, input) =>
      json("POST", `/pages/${encodeURIComponent(pageId)}/comment-threads/${encodeURIComponent(threadId)}/reopen`, input),
    listPresence: (pageId, options) =>
      request(`/pages/${encodeURIComponent(pageId)}/presence`, options),
    heartbeatPresence: (pageId, sessionId) =>
      request(`/pages/${encodeURIComponent(pageId)}/presence/${encodeURIComponent(sessionId)}/heartbeat`, { method: "POST" }),
    leavePresence: (pageId, sessionId) =>
      request(`/pages/${encodeURIComponent(pageId)}/presence/${encodeURIComponent(sessionId)}`, { method: "DELETE" }),
  });
}
