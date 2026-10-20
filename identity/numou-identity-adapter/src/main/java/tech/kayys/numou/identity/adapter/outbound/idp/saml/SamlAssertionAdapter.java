package tech.kayys.numou.identity.adapter.outbound.idp.saml;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.identity.application.dto.FederationSettings;
import tech.kayys.numou.identity.application.port.FederationSettingsPort;
import tech.kayys.numou.identity.application.port.SamlAssertionPort;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfig;
import tech.kayys.numou.identity.domain.user.FederatedIdentity;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@ApplicationScoped
public class SamlAssertionAdapter implements SamlAssertionPort {

    private static final Logger LOG = Logger.getLogger(SamlAssertionAdapter.class);

    private static final String SAML_ASSERTION_NS = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String SAML_PROTOCOL_NS = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String DSIG_NS = "http://www.w3.org/2000/09/xmldsig#";

    private final FederationSettingsPort settingsPort;
    private final DocumentBuilder documentBuilder;
    private final XPath xpath;
    private final Map<String, Long> consumedAssertionIds = new ConcurrentHashMap<>();

    @Inject
    public SamlAssertionAdapter(FederationSettingsPort settingsPort) throws Exception {
        this.settingsPort = settingsPort;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        this.documentBuilder = factory.newDocumentBuilder();
        this.xpath = XPathFactory.newInstance().newXPath();
    }

    @Override
    public Uni<FederatedIdentity> validateResponse(
            IdentityProviderConfig config, String samlResponse, String expectedInResponseTo) {
        Objects.requireNonNull(config, "config cannot be null");
        FederationSettings settings = settingsPort.parse(config);
        return Uni.createFrom()
                .<FederatedIdentity>completionStage(() -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return validateBlocking(
                                config.id().value().toString(), settings, samlResponse, expectedInResponseTo);
                    } catch (Exception e) {
                        throw new java.util.concurrent.CompletionException(e);
                    }
                }))
                .onFailure()
                .transform(ex -> {
                    if (ex instanceof SamlValidationException) {
                        return ApplicationError.of("SAML_INVALID", ex.getMessage()).toException();
                    }
                    return ApplicationError.of("SAML_ERROR", ex.getClass().getSimpleName()).toException();
                });
    }

    @Override
    public String serviceProviderMetadata(IdentityProviderConfig config) {
        FederationSettings settings = settingsPort.parse(config);
        String entityId = settings.samlEntityId();
        if (entityId.isBlank()) {
            throw new IllegalArgumentException("samlEntityId is required for metadata");
        }
        String acsUrl = settings.samlAcsUrl();
        if (acsUrl.isBlank()) {
            acsUrl = "https://UNKNOWN/callback";
        }
        return """
                <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata"
                                     entityID="%s">
                  <md:SPSSODescriptor
                      AuthnRequestsSigned="false"
                      WantAssertionsSigned="true"
                      protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                    <md:SingleLogoutService
                        Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect"
                        Location="%s"/>
                    <md:AssertionConsumerService
                        Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
                        Location="%s"
                        index="1" isDefault="true"/>
                  </md:SPSSODescriptor>
                </md:EntityDescriptor>""".formatted(entityId, acsUrl, acsUrl);
    }

    private FederatedIdentity validateBlocking(
            String providerId, FederationSettings settings, String samlResponse, String expectedInResponseTo)
            throws SamlValidationException {

        Document document;
        try {
            byte[] bytes = Base64.getDecoder().decode(samlResponse.trim());
            document = documentBuilder.parse(new java.io.ByteArrayInputStream(bytes));
        } catch (Exception ex) {
            throw new SamlValidationException("SAMLResponse is not valid base64 XML", ex);
        }
        document.getDocumentElement().normalize();

        Element response = document.getDocumentElement();
        if (!isSamlElement(response, "Response")) {
            throw new SamlValidationException("Root element is not a SAML Response");
        }

        String inResponseTo = response.getAttribute("InResponseTo");
        if (expectedInResponseTo != null && !expectedInResponseTo.isBlank()
                && !expectedInResponseTo.equals(inResponseTo)) {
            throw new SamlValidationException("InResponseTo does not match the issued request");
        }
        if (inResponseTo != null && !inResponseTo.isBlank()) {
            long now = Instant.now().toEpochMilli();
            Long previous = consumedAssertionIds.putIfAbsent(inResponseTo, now + TimeUnit.MINUTES.toMillis(5));
            if (previous != null && previous > now) {
                throw new SamlValidationException("Assertion already consumed (replay)");
            }
        }

        verifySignature(settings, response, document);

        Element assertion = findChild(response, SAML_ASSERTION_NS, "Assertion");
        if (assertion == null) {
            throw new SamlValidationException("Response contains no Assertion");
        }

        // Conditions
        Element conditions = findChild(assertion, SAML_ASSERTION_NS, "Conditions");
        if (conditions != null) {
            String notBefore = conditions.getAttribute("NotBefore");
            String notOnOrAfter = conditions.getAttribute("NotOnOrAfter");
            long now = Instant.now().getEpochSecond();
            long skew = 300; // 5 minutes clock tolerance
            if (!notBefore.isBlank() && now < parseInstant(notBefore) - skew) {
                throw new SamlValidationException("Assertion not yet valid");
            }
            if (!notOnOrAfter.isBlank() && now > parseInstant(notOnOrAfter) + skew) {
                throw new SamlValidationException("Assertion has expired");
            }
            String audience = xpathString(
                    "saml:AudienceRestriction/saml:Audience/text()", assertion).trim();
            if (!audience.isBlank() && !audience.equals(settings.samlEntityId())) {
                throw new SamlValidationException("Audience does not match this service provider");
            }
        }

        // Subject / NameID
        Element subject = findChild(assertion, SAML_ASSERTION_NS, "Subject");
        String nameId = null;
        if (subject != null) {
            Element nameIdEl = findChild(subject, SAML_ASSERTION_NS, "NameID");
            if (nameIdEl != null) {
                nameId = nameIdEl.getTextContent();
            }
        }
        if (nameId == null || nameId.isBlank()) {
            nameId = firstAttributeValue(assertion, settings.samlNameIdAttributes());
        }
        if (nameId == null || nameId.isBlank()) {
            throw new SamlValidationException("Assertion asserts no subject");
        }

        // Groups + informational attributes
        List<String> groups = new ArrayList<>();
        Map<String, String> claims = new LinkedHashMap<>();
        Element statements = findChild(assertion, SAML_ASSERTION_NS, "AttributeStatement");
        if (statements != null) {
            NodeList attributes = statements.getElementsByTagNameNS(SAML_ASSERTION_NS, "Attribute");
            for (int i = 0; i < attributes.getLength(); i++) {
                Element attribute = (Element) attributes.item(i);
                String attributeName = attribute.getAttribute("Name");
                List<String> values = attributeValues(attribute);
                if (values.isEmpty()) {
                    continue;
                }
                claims.put(attributeName, values.get(0));
                if (settings.samlAttributeToGroups().contains(attributeName)) {
                    groups.addAll(values);
                }
            }
        }

        String email = firstMatching(claims, List.of("email", "mail", "EmailAddress"));
        String displayName = firstMatching(claims, List.of("displayName", "display_name", "cn"));

        return new FederatedIdentity(
                providerId,
                nameId,
                email == null ? "" : email,
                displayName.isBlank() ? null : displayName,
                Set.copyOf(groups),
                claims
        );
    }

    private void verifySignature(FederationSettings settings, Element response, Document document)
            throws SamlValidationException {
        String certPem = settings.samlIdpCertificatePem();
        if (certPem == null || certPem.isBlank()) {
            throw new SamlValidationException("samlIdpCertificatePem is not configured");
        }
        Element signature = findChild(response, DSIG_NS, "Signature");
        if (signature == null) {
            throw new SamlValidationException("Response is not signed");
        }

        try {
            PublicKey publicKey = loadPublicKey(certPem);
            DOMValidateContext context = new DOMValidateContext(publicKey, signature);
            XMLSignature xmlSignature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context);
            if (!xmlSignature.validate(context)) {
                throw new SamlValidationException("XML signature verification failed");
            }
        } catch (SamlValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SamlValidationException("Could not verify signature: " + ex.getClass().getSimpleName(), ex);
        }
    }

    private static boolean isSamlElement(Element element, String localName) {
        return SAML_PROTOCOL_NS.equals(element.getNamespaceURI())
                && localName.equals(element.getLocalName());
    }

    private static Element findChild(Element parent, String namespace, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element
                    && namespace.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) {
                return element;
            }
        }
        return null;
    }

    private static long parseInstant(String value) {
        try {
            return Instant.parse(value).getEpochSecond();
        } catch (Exception ex) {
            return 0;
        }
    }

    private String firstAttributeValue(Element assertion, List<String> candidateNames) {
        for (String name : candidateNames) {
            String expression = "saml:AttributeStatement/saml:Attribute[@Name='%s']/saml:AttributeValue/text()"
                    .formatted(name);
            String value = xpathString(expression, assertion).trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String xpathString(String expression, Node context) {
        try {
            return (String) xpath.evaluate(expression, context, XPathConstants.STRING);
        } catch (XPathExpressionException ex) {
            return "";
        }
    }
    private static List<String> attributeValues(Element attribute) {
        List<String> values = new ArrayList<>();
        NodeList attributeValues = attribute.getElementsByTagNameNS(SAML_ASSERTION_NS, "AttributeValue");
        for (int i = 0; i < attributeValues.getLength(); i++) {
            values.add(attributeValues.item(i).getTextContent());
        }
        return values;
    }

    private static String firstMatching(Map<String, String> claims, List<String> names) {
        for (String name : names) {
            String value = claims.get(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static PublicKey loadPublicKey(String certPem) throws Exception {
        String pem = certPem
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");
        java.security.cert.CertificateFactory factory =
                java.security.cert.CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) factory.generateCertificate(
                new java.io.ByteArrayInputStream(Base64.getDecoder().decode(pem)));
        return cert.getPublicKey();
    }

    // APPEND-POINT
}
