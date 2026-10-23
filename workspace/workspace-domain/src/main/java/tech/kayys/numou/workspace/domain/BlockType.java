package tech.kayys.numou.workspace.domain;
/** Stable editor primitives. Domain-specific records remain opaque DOMAIN_REFERENCE blocks. */
public enum BlockType {
 PARAGRAPH, HEADING, BULLET, NUMBERED_ITEM, TODO, CHECKLIST_ITEM,
 QUOTE, CALLOUT, CODE, TABLE, TOGGLE, DIVIDER, FILE, DOMAIN_REFERENCE
}
