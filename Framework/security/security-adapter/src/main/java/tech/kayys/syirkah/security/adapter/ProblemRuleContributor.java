package tech.kayys.syirkah.security.adapter;

import tech.kayys.syirkah.security.application.problem.ProblemMapper;

/** Implement as a CDI bean to add HTTP mappings for a bounded context's own exceptions. */
public interface ProblemRuleContributor {

    void contribute(ProblemMapper mapper);
}
