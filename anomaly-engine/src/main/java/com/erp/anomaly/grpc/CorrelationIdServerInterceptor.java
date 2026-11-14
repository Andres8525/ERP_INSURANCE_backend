package com.erp.anomaly.grpc;

import com.erp.grpc.GrpcMetadata;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.util.UUID;

public final class CorrelationIdServerInterceptor implements ServerInterceptor {
    public static final Context.Key<String> CORRELATION_ID = Context.key("correlation-id");
    private static final System.Logger LOG = System.getLogger(CorrelationIdServerInterceptor.class.getName());

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                                                       ServerCallHandler<Q, R> next) {
        String incoming = headers.get(GrpcMetadata.CORRELATION_ID);
        String correlationId = incoming == null || incoming.isBlank() ? UUID.randomUUID().toString() : incoming;
        long startedAt = System.nanoTime();
        String method = call.getMethodDescriptor().getFullMethodName();

        ServerCall<Q, R> tracked = new SimpleForwardingServerCall<>(call) {
            @Override
            public void sendHeaders(Metadata responseHeaders) {
                responseHeaders.put(GrpcMetadata.CORRELATION_ID, correlationId);
                super.sendHeaders(responseHeaders);
            }

            @Override
            public void close(Status status, Metadata trailers) {
                long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
                LOG.log(System.Logger.Level.INFO, "grpc {0} status={1} correlationId={2} durationMs={3}",
                        method, status.getCode(), correlationId, elapsedMs);
                super.close(status, trailers);
            }
        };
        return Contexts.interceptCall(Context.current().withValue(CORRELATION_ID, correlationId), tracked, headers, next);
    }
}
