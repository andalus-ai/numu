package tech.kayys.numou.content.domain.model;

/** Portable field types; deliberately independent of any CMS frontend or JSON-schema vendor. */
public enum FieldType {
    STRING, TEXT, INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME, EMAIL, URL, JSON
}
