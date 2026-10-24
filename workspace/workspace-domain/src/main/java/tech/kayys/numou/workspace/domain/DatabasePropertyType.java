package tech.kayys.numou.workspace.domain;

/** Portable value types for generic workspace tables. Domain modules remain authoritative for their own records. */
public enum DatabasePropertyType {
    TEXT,
    NUMBER,
    BOOLEAN,
    DATE,
    DATE_TIME,
    SELECT,
    MULTI_SELECT,
    RELATION
}
