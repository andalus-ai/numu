package tech.kayys.numou.identity.adapter.outbound.idp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.identity.application.dto.FederationSettings;
import tech.kayys.numou.identity.application.port.FederationSettingsPort;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decodes the JSON {@code connectionConfigJson} of an identity provider configuration into the
 * neutral {@link FederationSettings} the application layer and adapters share.
 *
 * <p>Expected shape (all fields optional except {@code issuer} or explicit endpoints):
 * <pre>{@code
 * {
 *   "issuer": "https://login.example.com",
 *   "clientId": "numou",
 *   "clientSecret": "...",
 *   "redirectUri": "https://numou.example.com/api/v1/auth/federated/{providerId}/callback",
 *   "scopes": ["openid", "email", "profile", "groups"],
 *   "authorizationEndpoint": "", "tokenEndpoint": "", "jwksUri": "",
 *   "groupRoleMappings": { "admins": "<role-uuid>" },
 *   "defaultScopePath": "system",
 *   "defaultScopeType": "SYSTEM"
 * }
 * }</pre>
 */
@ApplicationScoped
public class JacksonFederationSettingsParser implements FederationSettingsPort {

    private final ObjectMapper objectMapper;

    @Inject
    public JacksonFederationSettingsParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public FederationSettings parse(IdentityProviderConfig config) {
        final JsonNode root;
        try {
            String json = config.connectionConfigJson();
            root = (json == null || json.isBlank())
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Identity provider '" + config.name() + "' has an unparsable connectionConfigJson", ex);
        }

        List<String> scopes = new ArrayList<>();
        JsonNode scopesNode = root.path("scopes");
        if (scopesNode.isArray()) {
            scopesNode.forEach(node -> {
                if (!node.isNull()) {
                    scopes.add(node.asText());
                }
            });
        }

        Map<String, String> groupRoleMappings = new LinkedHashMap<>();
        JsonNode mappings = root.path("groupRoleMappings");
        if (mappings.isObject()) {
            mappings.fields().forEachRemaining(entry ->
                    groupRoleMappings.put(entry.getKey(), entry.getValue().asText(null)));
        }

        return new FederationSettings(
                text(root, "issuer"),
                text(root, "clientId"),
                text(root, "clientSecret"),
                text(root, "redirectUri"),
                scopes,
                text(root, "authorizationEndpoint"),
                text(root, "tokenEndpoint"),
                text(root, "jwksUri"),
                groupRoleMappings,
                text(root, "defaultScopePath"),
                text(root, "defaultScopeType"),
                text(root, "ldapUrl"),
                text(root, "ldapBaseDn"),
                text(root, "ldapBindDn"),
                text(root, "ldapBindPassword"),
                text(root, "ldapUserSearchFilter"),
                text(root, "ldapGroupSearchBase"),
                text(root, "ldapGroupFilter"),
                text(root, "samlEntityId"),
                text(root, "samlAcsUrl"),
                text(root, "samlIdpSsoUrl"),
                text(root, "samlIdpCertificatePem"),
                textList(root, "samlNameIdAttributes"),
                textList(root, "samlAttributeToGroups")
        );
    }

    private static List<String> textList(JsonNode root, String field) {
        List<String> values = new ArrayList<>();
        JsonNode node = root.path(field);
        if (node.isArray()) {
            node.forEach(element -> {
                if (element.isTextual()) {
                    values.add(element.asText());
                }
            });
        }
        return values.isEmpty() ? null : values;
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isTextual() ? node.asText() : "";
    }
}
