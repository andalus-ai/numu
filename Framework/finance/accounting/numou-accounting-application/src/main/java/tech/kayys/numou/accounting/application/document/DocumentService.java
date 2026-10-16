package tech.kayys.syirkah.accounting.application.document;

import tech.kayys.syirkah.accounting.domain.document.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application service orchestrating document lifecycle, storage, and evidence links.
 */
public final class DocumentService {

    private final DocumentStorage storage;
    private final Map<DocumentId, Document> documents = new ConcurrentHashMap<>();
    private final List<DocumentLink> links = Collections.synchronizedList(new ArrayList<>());

    public DocumentService(DocumentStorage storage) {
        this.storage = Objects.requireNonNull(storage);
    }

    public Document upload(DocumentId id, DocumentType type, DocumentClassification classification,
                           String filename, String contentType, byte[] payload) {
        String hash = computeSha256(payload);
        String storageKey = "docs/" + id.value() + "/v1-" + filename;
        storage.store(storageKey, payload);

        DocumentMetadata meta = DocumentMetadata.of(filename, contentType, payload.length);
        Document doc = new Document(id, type, classification, meta, hash, storageKey);
        documents.put(doc.id(), doc);
        return doc;
    }

    public DocumentVersion addVersion(DocumentId id, String filename, String contentType, byte[] payload) {
        Document doc = documents.get(id);
        if (doc == null) throw new IllegalArgumentException("Document not found: " + id.value());

        String hash = computeSha256(payload);
        int nextVer = doc.versions().size() + 1;
        String storageKey = "docs/" + id.value() + "/v" + nextVer + "-" + filename;
        storage.store(storageKey, payload);

        DocumentMetadata meta = DocumentMetadata.of(filename, contentType, payload.length);
        return doc.addVersion(hash, storageKey, meta);
    }

    public DocumentLink linkToAggregate(DocumentId docId, String aggregateType, String aggregateId, String relationship) {
        if (!documents.containsKey(docId)) throw new IllegalArgumentException("Document not found: " + docId.value());
        DocumentLink link = DocumentLink.create(docId, aggregateType, aggregateId, relationship);
        links.add(link);
        return link;
    }

    public List<DocumentLink> findEvidenceChain(String aggregateType, String aggregateId) {
        return links.stream()
                .filter(l -> l.aggregateType().equals(aggregateType) && l.aggregateId().equals(aggregateId))
                .toList();
    }

    public Optional<Document> find(DocumentId id) { return Optional.ofNullable(documents.get(id)); }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }
}
