package tech.kayys.numou.plugin.application.resolution;

import tech.kayys.numou.plugin.application.manifest.*;

import java.util.*;

public final class ApplicationCapabilityResolver {
    public CapabilityResolution resolve(ApplicationManifest manifest, Collection<CapabilityProvider> available) {
        Objects.requireNonNull(manifest, "Application manifest is required");
        Objects.requireNonNull(available, "Provider collection is required");
        Map<String, List<CapabilityProvider>> index = new TreeMap<>();
        Set<String> providerIds = new HashSet<>();
        for (CapabilityProvider provider : available) {
            if (!providerIds.add(provider.providerId()))
                throw new CapabilityResolutionException("Duplicate provider ID: " + provider.providerId());
            validateProviderVersion(provider);
            index.computeIfAbsent(provider.capabilityId(), ignored -> new ArrayList<>()).add(provider);
        }
        index.values().forEach(list -> list.sort(Comparator.comparing(CapabilityProvider::providerId)));

        Map<String, CapabilityProvider> selected = new TreeMap<>();
        List<String> unavailable = new ArrayList<>();
        for (CapabilityRequirement requirement : manifest.requiredCapabilities())
            select(requirement, index, selected, false, unavailable);
        for (CapabilityRequirement requirement : manifest.optionalCapabilities())
            select(requirement, index, selected, true, unavailable);

        List<CapabilityProvider> order = new ArrayList<>();
        Map<String, Integer> state = new HashMap<>();
        Deque<String> path = new ArrayDeque<>();
        for (String capability : new ArrayList<>(selected.keySet()))
            visit(capability, selected, index, state, path, order);
        unavailable.sort(String::compareTo);
        return new CapabilityResolution(selected, order, unavailable);
    }

    private static void validateProviderVersion(CapabilityProvider provider) {
        try {
            SemanticVersion.parse(provider.contractVersion());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid contract version for provider " + provider.providerId()
                    + ": " + provider.contractVersion(), e);
        }
    }

    private static void select(CapabilityRequirement requirement, Map<String, List<CapabilityProvider>> index,
            Map<String, CapabilityProvider> selected, boolean optional, List<String> unavailable) {
        VersionRange range = VersionRange.parse(requirement.contractRange());
        CapabilityProvider alreadySelected = selected.get(requirement.id());
        if (alreadySelected != null) {
            if (!range.includes(SemanticVersion.parse(alreadySelected.contractVersion())))
                throw new CapabilityResolutionException("Selected provider " + alreadySelected.providerId()
                        + " supplies " + requirement.id() + "@" + alreadySelected.contractVersion()
                        + " which does not satisfy additional contract range " + requirement.contractRange());
            return;
        }
        List<CapabilityProvider> matches = index.getOrDefault(requirement.id(), List.of()).stream()
                .filter(p -> range.includes(SemanticVersion.parse(p.contractVersion()))).toList();
        if (matches.isEmpty()) {
            if (optional) { unavailable.add(requirement.id()); return; }
            throw new CapabilityResolutionException("Missing required capability provider for "
                    + requirement.id() + " matching contract range " + requirement.contractRange());
        }
        if (matches.size() != 1) {
            String candidates = matches.stream()
                    .map(provider -> provider.providerId() + "@" + provider.contractVersion())
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(", "));
            throw new CapabilityResolutionException("Expected exactly one compatible provider for "
                    + requirement.id() + " matching " + requirement.contractRange()
                    + " but found " + matches.size() + ": " + candidates);
        }
        selected.put(requirement.id(), matches.get(0));
    }

    private static void visit(String capability, Map<String, CapabilityProvider> selected,
            Map<String, List<CapabilityProvider>> index, Map<String, Integer> state,
            Deque<String> path, List<CapabilityProvider> order) {
        int current = state.getOrDefault(capability, 0);
        if (current == 2) return;
        if (current == 1) {
            List<String> cycle = new ArrayList<>(path);
            cycle.add(capability);
            throw new CapabilityResolutionException("Capability dependency cycle: " + String.join(" -> ", cycle));
        }
        CapabilityProvider provider = selected.get(capability);
        if (provider == null) return;
        state.put(capability, 1);
        path.addLast(capability);
        for (CapabilityRequirement dependency : provider.dependencies().stream()
                .sorted(Comparator.comparing(CapabilityRequirement::id)).toList()) {
            select(dependency, index, selected, false, new ArrayList<>());
            visit(dependency.id(), selected, index, state, path, order);
        }
        path.removeLast();
        state.put(capability, 2);
        order.add(provider);
    }
}
