package tech.kayys.numou.identity.application.security.abac;

public sealed interface PolicyCondition permits AllOf, AnyOf, Not, Comparison, Contains {}
