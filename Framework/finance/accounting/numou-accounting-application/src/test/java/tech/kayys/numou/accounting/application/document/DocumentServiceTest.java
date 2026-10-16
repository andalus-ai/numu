package tech.kayys.syirkah.accounting.application.document;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.document.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentServiceTest {

    @Test
    void upload_and_retrieve_document_with_evidence_chain() {
        var storage = new InMemoryDocumentStorage();
        var service = new DocumentService(storage);

        byte[] invoicePdf = "PDF-INVOICE-CONTENT".getBytes(StandardCharsets.UTF_8);
        var docId = DocumentId.generate();

        var doc = service.upload(
                docId, DocumentType.INVOICE, DocumentClassification.INTERNAL,
                "inv-1001.pdf", "application/pdf", invoicePdf);

        assertNotNull(doc);
        assertEquals(1, doc.versions().size());

        // Link to Vendor Invoice
        var link = service.linkToAggregate(docId, "VendorInvoice", "INV-1001", "PRIMARY_EVIDENCE");
        assertNotNull(link);

        List<DocumentLink> chain = service.findEvidenceChain("VendorInvoice", "INV-1001");
        assertEquals(1, chain.size());
        assertEquals(docId, chain.get(0).documentId());

        // Add version 2
        byte[] invoicePdfV2 = "PDF-INVOICE-REVISED".getBytes(StandardCharsets.UTF_8);
        var v2 = service.addVersion(docId, "inv-1001-v2.pdf", "application/pdf", invoicePdfV2);
        assertEquals(2, v2.versionNumber());

        var fetched = service.find(docId).orElseThrow();
        assertEquals(2, fetched.versions().size());
    }
}
