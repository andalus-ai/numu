package tech.kayys.numou.agent.application;

/** Authoritative authorization decision for exposing a tool to a caller. */
public interface AgentToolAuthorizationPort {
    boolean mayDiscover(AgentExecutionContext context, AgentToolDescriptor descriptor);
}