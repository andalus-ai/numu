package tech.kayys.numou.omnichannel.core;
import java.time.Duration;
/** Computes the delay before the next retry for a leased event. */
@FunctionalInterface
public interface InboundRetryPolicy { Duration delayFor(InboundWorkItem item); }
