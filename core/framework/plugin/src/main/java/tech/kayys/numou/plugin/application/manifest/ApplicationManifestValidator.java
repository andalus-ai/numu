package tech.kayys.numou.plugin.application.manifest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;

public final class ApplicationManifestValidator {
    private final ObjectMapper mapper;
    private final JsonSchema schema;

    public ApplicationManifestValidator(InputStream schemaDocument) {
        this(new ObjectMapper(), schemaDocument);
    }

    public ApplicationManifestValidator(ObjectMapper mapper, InputStream schemaDocument) {
        this.mapper = Objects.requireNonNull(mapper);
        Objects.requireNonNull(schemaDocument, "Schema document is required");
        this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaDocument);
    }

    public ApplicationManifest validate(String json, Set<String> knownPermissions) {
        Objects.requireNonNull(knownPermissions, "Known permission catalog is required");
        final JsonNode root;
        try { root = mapper.readTree(json); }
        catch (IOException | RuntimeException e) {
            throw new ManifestValidationException(List.of("Invalid JSON: " + e.getMessage()));
        }
        if (root == null || !root.isObject())
            throw new ManifestValidationException(List.of("Manifest root must be an object"));

        List<String> violations = new ArrayList<>();
        schema.validate(root).stream().map(ValidationMessage::getMessage).sorted().forEach(violations::add);
        if (!violations.isEmpty()) throw new ManifestValidationException(violations);

        JsonNode metadata = root.path("metadata");
        JsonNode requires = root.path("requires");
        List<CapabilityRequirement> required = requirements(
                requires.path("capabilities"), "requires.capabilities", violations);
        List<CapabilityRequirement> optional = requirements(
                requires.path("optionalCapabilities"), "requires.optionalCapabilities", violations);
        Map<String, CapabilityDeclaration> declarations = new TreeMap<>();
        collectCapabilityDeclarations(required, "required", declarations, violations);
        collectCapabilityDeclarations(optional, "optional", declarations, violations);

        Set<String> permissions = new TreeSet<>();
        root.path("permissions").forEach(p -> permissions.add(p.asText()));
        for (String permission : permissions)
            if (!knownPermissions.contains(permission)) violations.add("Unknown permission: " + permission);

        List<NavigationContribution> navigation = new ArrayList<>();
        Set<String> navigationIds = new HashSet<>();
        JsonNode nav = root.path("contributions").path("navigation");
        if (nav.isArray()) for (JsonNode n : nav) {
            String id = n.path("id").asText();
            String permission = n.path("requiredPermission").asText();
            if (!navigationIds.add(id)) violations.add("Duplicate navigation contribution ID: " + id);
            if (!permissions.contains(permission))
                violations.add("Navigation permission is not declared by the application: " + permission);
            navigation.add(new NavigationContribution(id, n.path("label").asText(),
                    n.path("route").asText(), permission));
        }
        try { SemanticVersion.parse(metadata.path("version").asText()); }
        catch (IllegalArgumentException e) { violations.add(e.getMessage()); }
        String website = metadata.path("website").asText(null);
        if (website != null) try {
            if (!URI.create(website).isAbsolute()) violations.add("metadata.website must be an absolute URI");
        } catch (IllegalArgumentException e) { violations.add("metadata.website must be a valid URI"); }

        if (!violations.isEmpty()) {
            violations.sort(String::compareTo);
            throw new ManifestValidationException(violations);
        }
        return new ApplicationManifest(metadata.path("id").asText(), metadata.path("version").asText(),
                metadata.path("publisher").asText(), metadata.path("displayName").asText(),
                required, optional, permissions, navigation);
    }


    private static void collectCapabilityDeclarations(
            List<CapabilityRequirement> requirements,
            String declarationKind,
            Map<String, CapabilityDeclaration> declarations,
            List<String> violations) {
        for (CapabilityRequirement requirement : requirements) {
            CapabilityDeclaration previous = declarations.putIfAbsent(
                    requirement.id(),
                    new CapabilityDeclaration(declarationKind, requirement.contractRange()));
            if (previous == null) continue;

            if (!previous.kind().equals(declarationKind)) {
                violations.add("Capability cannot be both required and optional: " + requirement.id());
            } else if (!previous.contractRange().equals(requirement.contractRange())) {
                String firstRange = previous.contractRange().compareTo(requirement.contractRange()) <= 0
                        ? previous.contractRange() : requirement.contractRange();
                String secondRange = previous.contractRange().compareTo(requirement.contractRange()) <= 0
                        ? requirement.contractRange() : previous.contractRange();
                violations.add("Conflicting " + declarationKind + " capability contract ranges for "
                        + requirement.id() + ": " + firstRange + " and " + secondRange);
            } else {
                violations.add("Duplicate " + declarationKind + " capability requirement: "
                        + requirement.id());
            }
        }
    }

    private record CapabilityDeclaration(String kind, String contractRange) {}

    private static List<CapabilityRequirement> requirements(
            JsonNode array, String field, List<String> violations) {
        List<CapabilityRequirement> result = new ArrayList<>();
        if (array.isArray()) {
            for (int i = 0; i < array.size(); i++) {
                JsonNode item = array.get(i);
                try {
                    result.add(new CapabilityRequirement(
                            item.path("id").asText(), item.path("contractRange").asText()));
                } catch (IllegalArgumentException e) {
                    violations.add(field + "[" + i + "].contractRange: " + e.getMessage());
                }
            }
        }
        return result;
    }
}
