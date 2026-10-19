package tech.kayys.numou.asset.application.query;

import io.smallrye.mutiny.Uni;

public class SearchAssetsHandler {

    private final AssetReadRepository readRepository;

    public SearchAssetsHandler(AssetReadRepository readRepository) {
        this.readRepository = readRepository;
    }

    public Uni<AssetPage<AssetView>> handle(AssetSearchCriteria criteria) {
        return Uni.createFrom().completionStage(() -> readRepository.search(criteria));
    }
}
