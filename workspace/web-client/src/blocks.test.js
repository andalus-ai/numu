import test from "node:test";
import assert from "node:assert/strict";
import {
  BLOCK_TYPES,
  blockTypeLabel,
  blocksFromPlainText,
  createBlock,
  moveBlock,
  normalizeBlocks,
  serializeBlocks,
} from "./blocks.js";

test("normalizes legacy and malformed block data without dropping custom fields", () => {
  const blocks = normalizeBlocks([
    { id: "b-1", type: "paragraph", text: "Hello", attributes: { color: "blue" }, extension: "kept" },
    { type: "custom-widget", text: 42, attributes: ["invalid"] },
    null,
  ]);

  assert.equal(blocks[0].id, "b-1");
  assert.equal(blocks[0].extension, "kept");
  assert.deepEqual(blocks[0].attributes, { color: "blue" });
  assert.equal(blocks[1].type, "custom-widget");
  assert.equal(blocks[1].text, "");
  assert.deepEqual(blocks[1].attributes, {});
  assert.equal(blocks[2].type, "paragraph");
  assert.deepEqual(blocks.map((block) => block.position), [0, 1, 2]);
});

test("creates only supported new block types and retains stable existing identifiers", () => {
  assert.deepEqual(BLOCK_TYPES, [
    "paragraph", "heading-1", "heading-2", "bulleted-list-item",
    "numbered-list-item", "quote", "code", "callout", "divider",
  ]);
  assert.equal(createBlock("quote").type, "quote");
  assert.equal(createBlock("not-supported").type, "paragraph");

  const converted = blocksFromPlainText("first\n\nsecond", [
    { id: "stable-1", type: "heading-1", attributes: { level: 1 } },
  ]);
  assert.equal(converted[0].id, "stable-1");
  assert.equal(converted[0].type, "heading-1");
  assert.deepEqual(converted[0].attributes, { level: 1 });
  assert.equal(converted[1].type, "paragraph");
});

test("reorders blocks and rewrites contiguous positions without changing IDs", () => {
  const input = [
    { id: "a", type: "paragraph", text: "A" },
    { id: "b", type: "quote", text: "B" },
    { id: "c", type: "code", text: "C" },
  ];
  const moved = moveBlock(input, 2, 0);
  assert.deepEqual(moved.map((block) => block.id), ["c", "a", "b"]);
  assert.deepEqual(moved.map((block) => block.position), [0, 1, 2]);
  assert.deepEqual(input.map((block) => block.id), ["a", "b", "c"]);
  assert.deepEqual(moveBlock(input, -1, 2).map((block) => block.id), ["a", "b", "c"]);
});

test("serializes typed blocks while preserving extension attributes and IDs", () => {
  const result = serializeBlocks([
    { id: "block-1", type: "callout", text: "Important", attributes: { tone: "warning" } },
    { id: "block-2", type: "divider", attributes: { style: "solid" } },
  ]);
  assert.deepEqual(result.map((block) => block.position), [0, 1]);
  assert.equal(result[0].id, "block-1");
  assert.deepEqual(result[0].attributes, { tone: "warning" });
  assert.equal(result[1].text, "");
  assert.equal(blockTypeLabel("callout"), "Callout");
  assert.equal(blockTypeLabel("vendor-extension"), "Custom block");
});
