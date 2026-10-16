package tech.kayys.numou.commerce.promotion.domain.compiler;

import tech.kayys.numou.commerce.promotion.domain.target.LineTarget;
import tech.kayys.numou.commerce.promotion.domain.target.SingleTargetSelection;

public final class CurrentLineTargetFactory implements CompiledTargetFactory {

    private static final TargetType TYPE = TargetType.of("current_line");

    @Override
    public TargetType type() {
        return TYPE;
    }

    @Override
    public CompiledTarget compile(TargetDefinition definition) {
        return context -> {
            var lineId = context.currentLineId().orElseThrow(
                    () -> new IllegalStateException("current_line target requires currentLineId"));
            return new SingleTargetSelection(new LineTarget(lineId.value()));
        };
    }
}
