package tech.kayys.syirkah.identity.application.security.abac;

public sealed interface PolicyCondition permits AllOf, AnyOf, Not, Comparison, Contains {}
