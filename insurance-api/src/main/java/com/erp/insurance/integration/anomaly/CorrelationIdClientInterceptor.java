package com.erp.insurance.integration.anomaly;

import com.erp.grpc.GrpcMetadata;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

import java.util.UUID;

public final class CorrelationIdClientInterceptor implements ClientInterceptor {
    public static final CallOptions.Key<String> CORRELATION_ID = CallOptions.Key.create("correlation-id");

    @Override
    public <Q, R> ClientCall<Q, R> interceptCall(MethodDescriptor<Q, R> method, CallOptions options, Channel next) {
        String fromOptions = options.getOption(CORRELATION_ID);
        String correlationId = fromOptions == null ? UUID.randomUUID().toString() : fromOptions;
        return new SimpleForwardingClientCall<>(next.newCall(method, options)) {
            @Override
            public void start(Listener<R> listener, Metadata headers) {
                headers.put(GrpcMetadata.CORRELATION_ID, correlationId);
                super.start(listener, headers);
            }
        };
    }
}
