package tech.kayys.syirkah.commerce.promotion.domain.compiler;

public interface CompiledTargetFactory {

    TargetType type();

    CompiledTarget compile(TargetDefinition definition);
}
