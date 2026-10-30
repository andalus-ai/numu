export const EDITABLE_BLOCK_TYPES = Object.freeze([
  "PARAGRAPH", "HEADING", "BULLET", "NUMBERED_ITEM", "TODO",
  "CHECKLIST_ITEM", "QUOTE", "CALLOUT", "CODE", "TABLE", "TOGGLE",
  "DIVIDER",
]);

export const BLOCK_LABELS = Object.freeze({
  PARAGRAPH: "Paragraph", HEADING: "Heading", BULLET: "Bulleted list",
  NUMBERED_ITEM: "Numbered list", TODO: "To-do", CHECKLIST_ITEM: "Checklist",
  QUOTE: "Quote", CALLOUT: "Callout", CODE: "Code", TABLE: "Table",
  TOGGLE: "Toggle", DIVIDER: "Divider", FILE: "File attachment",
  DOMAIN_REFERENCE: "Domain reference",
});

const asAttributes = (value) => value && typeof value === "object" && !Array.isArray(value)
  ? { ...value } : {};

export function normalizeBlocks(blocks) {
  if (!Array.isArray(blocks)) return [];
  return blocks.map((block, index) => ({
    id: typeof block?.id === "string" && block.id ? block.id : crypto.randomUUID(),
    type: typeof block?.type === "string" ? block.type.toUpperCase() : "PARAGRAPH",
    text: typeof block?.text === "string" ? block.text : "",
    position: index,
    attributes: asAttributes(block?.attributes),
  }));
}

export function createBlock(type = "PARAGRAPH", position = 0) {
  const normalizedType = String(type).toUpperCase();
  if (!EDITABLE_BLOCK_TYPES.includes(normalizedType)) {
    throw new TypeError("Unsupported editable block type");
  }
  return {
    id: crypto.randomUUID(),
    type: normalizedType,
    text: "",
    position,
    attributes: normalizedType === "CALLOUT" ? { tone: "info" }
      : normalizedType === "CHECKLIST_ITEM" || normalizedType === "TODO" ? { checked: "false" }
      : {},
  };
}

export function serializeBlocks(blocks) {
  return normalizeBlocks(blocks).map((block, position) => ({
    ...block,
    position,
  }));
}
