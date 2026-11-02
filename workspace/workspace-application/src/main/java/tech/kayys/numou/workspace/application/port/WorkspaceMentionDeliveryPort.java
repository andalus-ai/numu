package tech.kayys.numou.workspace.application.port;
/** Host adapter that delivers a mention to Notification or another inbox provider. */
@FunctionalInterface public interface WorkspaceMentionDeliveryPort {
 void deliver(WorkspaceMentionOutboxStore.ClaimedMention mention) throws Exception;
}
