package tech.kayys.numou.accounting.domain.workflow;

/** Lifecycle states of a {@link ProcessInstance}. */
public enum ProcessStatus { RUNNING, WAITING, COMPLETED, FAILED, CANCELLED }
