package tech.kayys.numou.asset.application.query;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;

public class GetAssetHandler {

    private final AssetReadRepository readRepository;

    public GetAssetHandler(AssetReadRepository readRepository) {
        this.readRepository = readRepository;
    }

    public Uni<Result<AssetView>> handle(GetAssetQuery query) {
        return Uni.createFrom()
                .completionStage(() -> readRepository.findById(query.tenantId(), query.assetId().value()))
                .map(opt -> opt.orElse(null))
                .onItem().ifNull().failWith(() -> new ApplicationErrorException(
                        ApplicationError.of("asset.not-found", "Asset not found: " + query.assetId().value())))
                .map(Result::success);
    }
}
