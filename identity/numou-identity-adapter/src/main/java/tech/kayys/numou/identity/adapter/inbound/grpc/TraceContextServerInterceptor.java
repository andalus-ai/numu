package tech.kayys.numou.identity.adapter.inbound.grpc;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Propagates {@code x-trace-id} across gRPC calls and exposes it as {@code traceId} in the MDC
 * for every listener callback, so one log line can be stitched to the caller's trace.
 *
 * <p>The id is accepted only when it looks sane (otherwise regenerated) and is echoed back in the
 * response headers so both sides of the call share the same trace.
 */
@ApplicationScoped
public class TraceContextServerInterceptor implements ServerInterceptor {

    public static final Metadata.Key<String> TRACE_ID =
            Metadata.Key.of("x-trace-id", Metadata.ASCII_STRING_MARSHALLER);

    private static final Pattern SAFE_TRACE_ID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");
    private static final String MDC_KEY = "traceId";

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String supplied = headers.get(TRACE_ID);
        final String traceId = supplied != null && SAFE_TRACE_ID.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();

        ServerCall<ReqT, RespT> echoingCall =
                new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                    @Override
                    public void sendHeaders(Metadata responseHeaders) {
                        responseHeaders.put(TRACE_ID, traceId);
                        super.sendHeaders(responseHeaders);
                    }
                };

        ServerCall.Listener<ReqT> delegate = next.startCall(echoingCall, headers);
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onMessage(ReqT message) {
                withTrace(() -> super.onMessage(message));
            }

            @Override
            public void onHalfClose() {
                withTrace(super::onHalfClose);
            }

            @Override
            public void onCancel() {
                withTrace(super::onCancel);
            }

            @Override
            public void onComplete() {
                withTrace(super::onComplete);
            }

            @Override
            public void onReady() {
                withTrace(super::onReady);
            }

            private void withTrace(Runnable action) {
                MDC.put(MDC_KEY, traceId);
                try {
                    action.run();
                } finally {
                    MDC.remove(MDC_KEY);
                }
            }
        };
    }
}
