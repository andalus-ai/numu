package tech.kayys.numou.commerce.promotion.domain.compiler;

public interface CompiledTargetFactory {

    TargetType type();

    CompiledTarget compile(TargetDefinition definition);
}
