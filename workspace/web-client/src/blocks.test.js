import test from "node:test";
import assert from "node:assert/strict";
import { createBlock, EDITABLE_BLOCK_TYPES, normalizeBlocks, serializeBlocks } from "./blocks.js";

test("normalizes persisted blocks without losing IDs, types, text, or attributes", () => {
  const blocks = normalizeBlocks([
    { id: "b-1", type: "heading", text: "Overview", position: 8, attributes: { level: "2" } },
    { id: "b-2", type: "FILE", text: "attachment", attributes: { fileId: "f-1" } },
  ]);
  assert.deepEqual(blocks.map(({ id, type, position }) => ({ id, type, position })), [
    { id: "b-1", type: "HEADING", position: 0 },
    { id: "b-2", type: "FILE", position: 1 },
  ]);
  assert.deepEqual(blocks[0].attributes, { level: "2" });
  assert.deepEqual(blocks[1].attributes, { fileId: "f-1" });
});

test("creates only domain-supported editable types with safe defaults", () => {
  assert.deepEqual(EDITABLE_BLOCK_TYPES, [
    "PARAGRAPH", "HEADING", "BULLET", "NUMBERED_ITEM", "TODO",
    "CHECKLIST_ITEM", "QUOTE", "CALLOUT", "CODE", "TABLE", "TOGGLE", "DIVIDER",
  ]);
  assert.equal(createBlock("callout").attributes.tone, "info");
  assert.equal(createBlock("todo").attributes.checked, "false");
  assert.throws(() => createBlock("FILE"), /Unsupported editable block type/);
});

test("serializes a stable, contiguous order and preserves opaque block metadata", () => {
  const blocks = serializeBlocks([
    { id: "b-2", type: "FILE", text: "", position: 9, attributes: { fileId: "f-2" } },
    { id: "b-1", type: "PARAGRAPH", text: "Hello", position: 4 },
  ]);
  assert.deepEqual(blocks.map((block) => block.position), [0, 1]);
  assert.deepEqual(blocks[0].attributes, { fileId: "f-2" });
  assert.equal(blocks[1].text, "Hello");
});
