package tech.kayys.numou.plugin.application.resolution;

import tech.kayys.numou.plugin.application.manifest.*;

import java.util.*;

public final class ApplicationCapabilityResolver {
    public CapabilityResolution resolve(ApplicationManifest manifest, Collection<CapabilityProvider> available) {
        Objects.requireNonNull(manifest, "Application manifest is required");
        Objects.requireNonNull(available, "Provider collection is required");
        Map<String, List<CapabilityProvider>> index = new TreeMap<>();
        List<CapabilityProvider> providerCatalog = new ArrayList<>(available.size());
        Set<String> providerIds = new HashSet<>();
        for (CapabilityProvider provider : available) {
            if (!providerIds.add(provider.providerId()))
                throw new CapabilityResolutionException("Duplicate provider ID: " + provider.providerId());
            validateProviderVersion(provider);
            providerCatalog.add(provider);
            index.computeIfAbsent(provider.capabilityId(), ignored -> new ArrayList<>()).add(provider);
        }
        validateProviderDependencies(providerCatalog);
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
        CapabilityResolutionReport report = buildReport(manifest, selected, unavailable);
        return new CapabilityResolution(selected, order, unavailable, report);
    }

    private static CapabilityResolutionReport buildReport(ApplicationManifest manifest,
            Map<String, CapabilityProvider> selected, List<String> unavailable) {
        List<CapabilityResolutionEdge> edges = new ArrayList<>();
        manifest.requiredCapabilities().forEach(requirement ->
                edges.add(edge(null, requirement, true, selected)));
        manifest.optionalCapabilities().forEach(requirement ->
                edges.add(edge(null, requirement, false, selected)));
        selected.forEach((capabilityId, provider) -> provider.dependencies().forEach(dependency ->
                edges.add(edge(capabilityId, dependency, true, selected))));
        edges.sort(Comparator
                .comparing(CapabilityResolutionEdge::sourceCapability,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(CapabilityResolutionEdge::targetCapability)
                .thenComparing(CapabilityResolutionEdge::contractRange)
                .thenComparing(CapabilityResolutionEdge::required, Comparator.reverseOrder()));
        return new CapabilityResolutionReport(new ArrayList<>(new TreeMap<>(selected).values()),
                edges, unavailable, List.of());
    }

    private static CapabilityResolutionEdge edge(String sourceCapability, CapabilityRequirement requirement,
            boolean required, Map<String, CapabilityProvider> selected) {
        CapabilityProvider provider = selected.get(requirement.id());
        return new CapabilityResolutionEdge(sourceCapability, requirement.id(), requirement.contractRange(),
                required, provider == null
                        ? CapabilityResolutionEdge.ResolutionStatus.UNAVAILABLE_OPTIONAL
                        : CapabilityResolutionEdge.ResolutionStatus.RESOLVED,
                provider == null ? null : provider.providerId(),
                provider == null ? null : provider.contractVersion());
    }

    private static void validateProviderDependencies(Collection<CapabilityProvider> providers) {
        List<String> diagnostics = new ArrayList<>();
        providers.stream().sorted(Comparator.comparing(CapabilityProvider::providerId)).forEach(provider -> {
            Map<String, List<String>> rangesByCapability = new TreeMap<>();
            provider.dependencies().forEach(dependency -> rangesByCapability
                    .computeIfAbsent(dependency.id(), ignored -> new ArrayList<>())
                    .add(dependency.contractRange()));
            rangesByCapability.forEach((capabilityId, ranges) -> {
                if (ranges.size() > 1) {
                    TreeSet<String> distinctRanges = new TreeSet<>(ranges);
                    if (distinctRanges.size() > 1) {
                        diagnostics.add("Provider " + provider.providerId()
                                + " declares conflicting dependency ranges for " + capabilityId
                                + ": " + String.join(", ", distinctRanges));
                    } else {
                        diagnostics.add("Provider " + provider.providerId()
                                + " declares duplicate dependency " + capabilityId
                                + " with range " + ranges.get(0));
                    }
                }
            });
        });
        if (!diagnostics.isEmpty()) throw new CapabilityResolutionException(diagnostics);
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
