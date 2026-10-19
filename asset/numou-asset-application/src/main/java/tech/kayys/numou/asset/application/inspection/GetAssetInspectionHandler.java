package tech.kayys.numou.asset.application.inspection;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;

public class GetAssetInspectionHandler {

    private final AssetInspectionReadRepository readRepository;

    public GetAssetInspectionHandler(AssetInspectionReadRepository readRepository) {
        this.readRepository = readRepository;
    }

    public Uni<Result<AssetInspectionReadModel>> handle(GetAssetInspectionQuery query) {
        return Uni.createFrom()
                .completionStage(() -> readRepository.findById(query.tenantId(), query.inspectionId()))
                .map(opt -> opt.orElse(null))
                .onItem().ifNull().failWith(() -> new ApplicationErrorException(
                        ApplicationError.of("asset.inspection-not-found",
                                "Inspection not found: " + query.inspectionId())))
                .map(Result::success);
    }
}
