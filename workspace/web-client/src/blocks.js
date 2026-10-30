export const BLOCK_TYPES = Object.freeze([
  "paragraph",
  "heading-1",
  "heading-2",
  "bulleted-list-item",
  "numbered-list-item",
  "quote",
  "code",
  "callout",
  "divider",
]);

const TYPE_LABELS = Object.freeze({
  paragraph: "Paragraph",
  "heading-1": "Heading 1",
  "heading-2": "Heading 2",
  "bulleted-list-item": "Bulleted list",
  "numbered-list-item": "Numbered list",
  quote: "Quote",
  code: "Code",
  callout: "Callout",
  divider: "Divider",
});

export function blockTypeLabel(type) {
  return TYPE_LABELS[type] || "Custom block";
}

export function normalizeBlocks(blocks) {
  if (!Array.isArray(blocks)) return [];
  return blocks.map((block, index) => ({
    ...block,
    type: typeof block?.type === "string" && block.type ? block.type : "paragraph",
    text: typeof block?.text === "string" ? block.text : "",
    position: index,
    attributes: block?.attributes && typeof block.attributes === "object" && !Array.isArray(block.attributes)
      ? { ...block.attributes }
      : {},
  }));
}

export function createBlock(type = "paragraph") {
  const normalizedType = BLOCK_TYPES.includes(type) ? type : "paragraph";
  return {
    type: normalizedType,
    text: "",
    position: 0,
    attributes: {},
  };
}

export function moveBlock(blocks, fromIndex, toIndex) {
  const next = normalizeBlocks(blocks);
  if (!Number.isInteger(fromIndex) || !Number.isInteger(toIndex)
      || fromIndex < 0 || fromIndex >= next.length
      || toIndex < 0 || toIndex >= next.length
      || fromIndex === toIndex) return next;
  const [moved] = next.splice(fromIndex, 1);
  next.splice(toIndex, 0, moved);
  return next.map((block, position) => ({ ...block, position }));
}

export function blocksFromPlainText(value, currentBlocks = []) {
  const existing = normalizeBlocks(currentBlocks);
  return String(value ?? "").split(/\n\s*\n/).map((text, index) => ({
    ...(existing[index]?.id ? { id: existing[index].id } : {}),
    type: existing[index]?.type || "paragraph",
    text,
    position: index,
    attributes: existing[index]?.attributes || {},
  }));
}

export function serializeBlocks(blocks) {
  return normalizeBlocks(blocks).map((block, position) => ({
    ...block,
    position,
  }));
}
