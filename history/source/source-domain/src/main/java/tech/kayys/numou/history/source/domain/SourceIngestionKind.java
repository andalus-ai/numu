package tech.kayys.numou.history.source.domain;

/** Input boundary for an ingestion request; binary payloads remain owned by the shared document capability. */
public enum SourceIngestionKind {
    URL_REFERENCE,
    DOCUMENT_REFERENCE,
    MEDIA_REFERENCE,
    ARCHIVAL_FEED_REFERENCE
}
