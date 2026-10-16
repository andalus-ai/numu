package tech.kayys.syirkah.groceries.application.api;

import tech.kayys.syirkah.groceries.application.api.command.*;
import tech.kayys.syirkah.groceries.application.api.query.*;
import tech.kayys.syirkah.groceries.domain.identifier.ScaleId;
import tech.kayys.syirkah.groceries.domain.identifier.ProductId;
import tech.kayys.syirkah.groceries.domain.valueobject.Weight;

import java.util.concurrent.CompletionStage;

/**
 * Public API for pos POS operations.
 */
public interface PosService {

    // ============ Product Operations ============

    CompletionStage<ProductId> registerProduct(RegisterProductCommand command);

    CompletionStage<ProductId> addBatchLot(AddBatchLotCommand command);

    CompletionStage<ProductId> updateShelfLife(UpdateShelfLifeCommand command);

    // ============ Scale Operations ============

    CompletionStage<ScaleId> registerScale(RegisterScaleCommand command);

    CompletionStage<ScaleId> connectScale(ConnectScaleCommand command);

    CompletionStage<WeightReadResult> readWeight(ReadWeightCommand command);

    CompletionStage<ScaleId> tareScale(TareScaleCommand command);

    // ============ Checkout Operations ============

    CompletionStage<CartItemResult> addWeightedItemToCart(AddWeightedItemCommand command);

    CompletionStage<Receipt> completeTransaction(CompleteTransactionCommand command);

    // ============ Expiry Management ============

    CompletionStage<ExpiryListResult> getProductsExpiringSoon(GetExpiringProductsQuery query);

    CompletionStage<Void> markProductsExpired(MarkExpiredProductsCommand command);

    CompletionStage<Void> processWaste(ProcessWasteCommand command);
}
