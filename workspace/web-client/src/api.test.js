import test from "node:test";
import assert from "node:assert/strict";
import { createWorkspaceApi, WorkspaceApiError } from "./api.js";

function response(status, payload, contentType = "application/json") {
  return {
    status,
    ok: status >= 200 && status < 300,
    headers: { get: (name) => name.toLowerCase() === "content-type" ? contentType : null },
    json: async () => payload,
  };
}

test("page update sends the optimistic revision and never sends tenant or actor identity", async () => {
  let observed;
  const api = createWorkspaceApi({
    fetchImpl: async (url, init) => {
      observed = { url, init };
      return response(200, { id: "p-1", revision: 3 });
    },
  });

  await api.updatePage("p-1", {
    expectedRevision: 2,
    title: "Updated",
    blocks: [],
    changeSummary: "Edit title",
  });

  assert.equal(observed.url, "/api/v1/pages/p-1");
  assert.equal(observed.init.method, "PUT");
  assert.equal(observed.init.credentials, "same-origin");
  assert.deepEqual(JSON.parse(observed.init.body), {
    expectedRevision: 2,
    title: "Updated",
    blocks: [],
    changeSummary: "Edit title",
  });
  assert.equal("tenantId" in JSON.parse(observed.init.body), false);
  assert.equal("actorId" in JSON.parse(observed.init.body), false);
});

test("encodes path and query values", async () => {
  const calls = [];
  const api = createWorkspaceApi({
    baseUrl: "https://api.example.test/api/v1/",
    fetchImpl: async (url) => {
      calls.push(url);
      return response(200, []);
    },
  });

  await api.listPages("space / 1", null);
  await api.searchPages("workspace 1", "budget & forecast", "space 2", 10);

  assert.equal(calls[0], "https://api.example.test/api/v1/spaces/space%20%2F%201/pages");
  assert.match(calls[1], /q=budget\+%26\+forecast/);
  assert.match(calls[1], /spaceId=space\+2/);
  assert.match(calls[1], /limit=10/);
});

test("maps revision conflict to a recoverable error", async () => {
  const api = createWorkspaceApi({
    fetchImpl: async () => response(409, { message: "internal detail" }),
  });

  await assert.rejects(
    api.updatePage("p-1", { expectedRevision: 1, title: "draft", blocks: [] }),
    (error) => error instanceof WorkspaceApiError
      && error.status === 409
      && error.code === "REVISION_CONFLICT"
      && !error.message.includes("internal detail"),
  );
});

test("maps inaccessible resources without revealing whether they exist", async () => {
  const api = createWorkspaceApi({
    fetchImpl: async () => response(403, { message: "private detail" }),
  });

  await assert.rejects(api.getPage("private-page"), (error) =>
    error.status === 403
      && error.message === "This workspace resource is unavailable or you do not have access."
      && !error.message.includes("private detail"));
});

test("turns transport failures into safe user-facing errors", async () => {
  const api = createWorkspaceApi({
    fetchImpl: async () => { throw new Error("secret endpoint/token"); },
  });

  await assert.rejects(api.getPage("p-1"), (error) =>
    error.code === "NETWORK_ERROR" && !error.message.includes("secret"));
});


test("lists tenant-authorized workspaces and spaces without sending identity", async () => {
  const calls = [];
  const api = createWorkspaceApi({
    fetchImpl: async (url, init) => {
      calls.push({ url, init });
      return response(200, []);
    },
  });

  await api.listWorkspaces();
  await api.listSpaces("workspace / 1");

  assert.equal(calls[0].url, "/api/v1/workspaces");
  assert.equal(calls[1].url, "/api/v1/workspaces/workspace%20%2F%201/spaces");
  assert.equal(calls[0].init.credentials, "same-origin");
  assert.equal(calls[1].init.credentials, "same-origin");
  assert.equal(calls[0].init.body, undefined);
  assert.equal(calls[1].init.body, undefined);
});

test("uses page-scoped comment endpoints and optimistic thread revisions", async () => {
  const calls = [];
  const api = createWorkspaceApi({
    fetchImpl: async (url, init) => {
      calls.push({ url, init });
      return response(200, { id: "thread-1", revision: 2 });
    },
  });

  await api.listCommentThreads("page / 1");
  await api.createCommentThread("page / 1", { body: "Question", anchorBlockId: "block-7" });
  await api.replyToCommentThread("page / 1", "thread / 1", {
    parentCommentId: "comment-1", body: "Reply",
  });
  await api.resolveCommentThread("page / 1", "thread / 1", { expectedRevision: 1 });
  await api.reopenCommentThread("page / 1", "thread / 1", { expectedRevision: 2 });

  assert.equal(calls[0].url, "/api/v1/pages/page%20%2F%201/comment-threads");
  assert.equal(calls[0].init.method, "GET");
  assert.equal(calls[1].init.method, "POST");
  assert.deepEqual(JSON.parse(calls[1].init.body), { body: "Question", anchorBlockId: "block-7" });
  assert.equal(calls[2].url, "/api/v1/pages/page%20%2F%201/comment-threads/thread%20%2F%201/replies");
  assert.equal(calls[3].url.endsWith("/resolve"), true);
  assert.deepEqual(JSON.parse(calls[3].init.body), { expectedRevision: 1 });
  assert.equal(calls[4].url.endsWith("/reopen"), true);
  assert.deepEqual(JSON.parse(calls[4].init.body), { expectedRevision: 2 });
  assert.ok(calls.every(({ init }) => init.credentials === "same-origin"));
});

test("suggests tenant-scoped page mention targets using query parameters", async () => {
  const calls = [];
  const api = createWorkspaceApi({
    fetchImpl: async (url, init) => {
      calls.push({ url, init });
      return response(200, [{ userId: "u-1", handle: "amina", displayName: "Amina" }]);
    },
  });
  const result = await api.suggestMentions("page / 1", "ami &", 6);
  assert.equal(calls[0].url, "/api/v1/pages/page%20%2F%201/mention-suggestions?q=ami+%26&limit=6");
  assert.equal(calls[0].init.method, "GET");
  assert.equal(calls[0].init.credentials, "same-origin");
  assert.equal(result[0].handle, "amina");
});

test("uses page-scoped presence heartbeat, listing, and leave endpoints", async () => {
  const calls = [];
  const api = createWorkspaceApi({
    fetchImpl: async (url, init) => {
      calls.push({ url, init });
      return response(init.method === "DELETE" ? 204 : 200, { pageId: "page-1", users: [] });
    },
  });

  await api.listPresence("page / 1");
  await api.heartbeatPresence("page / 1", "session / 1");
  await api.leavePresence("page / 1", "session / 1");

  assert.equal(calls[0].url, "/api/v1/pages/page%20%2F%201/presence");
  assert.equal(calls[0].init.method, "GET");
  assert.equal(calls[1].url, "/api/v1/pages/page%20%2F%201/presence/session%20%2F%201/heartbeat");
  assert.equal(calls[1].init.method, "POST");
  assert.equal(calls[2].url, "/api/v1/pages/page%20%2F%201/presence/session%20%2F%201");
  assert.equal(calls[2].init.method, "DELETE");
  assert.ok(calls.every(({ init }) => init.credentials === "same-origin"));
});

test("uses page-scoped presence heartbeat, listing, and leave endpoints", async () => {
  const calls = [];
  const api = createWorkspaceApi({
    fetchImpl: async (url, init) => {
      calls.push({ url, init });
      return response(init.method === "DELETE" ? 204 : 200, { pageId: "page-1", users: [] });
    },
  });
  await api.listPresence("page / 1");
  await api.heartbeatPresence("page / 1", "session / 1");
  await api.leavePresence("page / 1", "session / 1");
  assert.equal(calls[0].url, "/api/v1/pages/page%20%2F%201/presence");
  assert.equal(calls[0].init.method, "GET");
  assert.equal(calls[1].url, "/api/v1/pages/page%20%2F%201/presence/session%20%2F%201/heartbeat");
  assert.equal(calls[1].init.method, "POST");
  assert.equal(calls[1].init.body, undefined);
  assert.equal(calls[2].url, "/api/v1/pages/page%20%2F%201/presence/session%20%2F%201");
  assert.equal(calls[2].init.method, "DELETE");
  assert.ok(calls.every(({ init }) => init.credentials === "same-origin"));
});
