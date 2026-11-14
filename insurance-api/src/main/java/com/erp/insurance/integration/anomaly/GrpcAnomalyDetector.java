package com.erp.insurance.integration.anomaly;

import com.erp.anomaly.engine.AnomalyEngine;
import com.erp.anomaly.grpc.AnomalyEngineServices;
import com.erp.grpc.anomaly.v1.AnalyzeRequest;
import com.erp.grpc.anomaly.v1.AnomalyEngineGrpc;
import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@EnableConfigurationProperties(AnomalyEngineProperties.class)
public class GrpcAnomalyDetector implements AnomalyDetector, DisposableBean {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcAnomalyDetector.class);
    private static final Set<Status.Code> FALLBACK_CODES =
            Set.of(Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED, Status.Code.UNIMPLEMENTED);

    private final AnomalyEngineProperties properties;
    private ManagedChannel remoteChannel;
    private ManagedChannel localChannel;
    private Server localServer;

    public GrpcAnomalyDetector(AnomalyEngineProperties properties) {
        this.properties = properties;
    }

    @Override
    public Detection analyze(AnalyzeRequest request, String correlationId) {
        if (properties.hasRemote()) {
            try {
                return new Detection(call(remoteChannel(), request, correlationId), Mode.REMOTE);
            } catch (StatusRuntimeException exception) {
                if (!properties.fallbackInProcess() || !FALLBACK_CODES.contains(exception.getStatus().getCode())) {
                    throw exception;
                }
                LOG.warn("anomaly-engine remoto no disponible ({}), se usa el motor en proceso. correlationId={}",
                        exception.getStatus().getCode(), correlationId);
            }
        }
        return new Detection(call(localChannel(), request, correlationId), Mode.IN_PROCESS);
    }

    private com.erp.grpc.anomaly.v1.AnalyzeResponse call(ManagedChannel channel, AnalyzeRequest request,
                                                         String correlationId) {
        return AnomalyEngineGrpc.newBlockingStub(channel)
                .withOption(CorrelationIdClientInterceptor.CORRELATION_ID, correlationId)
                .withDeadlineAfter(properties.deadline().toMillis(), TimeUnit.MILLISECONDS)
                .analyze(request);
    }

    private synchronized ManagedChannel remoteChannel() {
        if (remoteChannel == null) {
            ChannelCredentials credentials = properties.tls()
                    ? TlsChannelCredentials.create()
                    : InsecureChannelCredentials.create();
            remoteChannel = Grpc.newChannelBuilder(properties.address(), credentials)
                    .defaultServiceConfig(retryPolicy())
                    .enableRetry()
                    .intercept(new CorrelationIdClientInterceptor())
                    .build();
        }
        return remoteChannel;
    }

    private synchronized ManagedChannel localChannel() {
        if (localChannel == null) {
            String name = "anomaly-engine-" + InProcessServerBuilder.generateName();
            try {
                localServer = InProcessServerBuilder.forName(name).directExecutor()
                        .addService(AnomalyEngineServices.engine(AnomalyEngine.withDefaultRules()))
                        .build().start();
            } catch (IOException exception) {
                throw new UncheckedIOException("No se pudo iniciar el motor de anomalías en proceso", exception);
            }
            localChannel = InProcessChannelBuilder.forName(name).directExecutor()
                    .intercept(new CorrelationIdClientInterceptor())
                    .build();
        }
        return localChannel;
    }

    static Map<String, ?> retryPolicy() {
        return Map.of("methodConfig", List.of(Map.of(
                "name", List.of(Map.of("service", AnomalyEngineGrpc.SERVICE_NAME)),
                "retryPolicy", Map.of(
                        "maxAttempts", 3.0,
                        "initialBackoff", "0.2s",
                        "maxBackoff", "1s",
                        "backoffMultiplier", 2.0,
                        "retryableStatusCodes", List.of("UNAVAILABLE")))));
    }

    @Override
    public synchronized void destroy() throws InterruptedException {
        for (ManagedChannel channel : new ManagedChannel[] {remoteChannel, localChannel}) {
            if (channel != null) channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (localServer != null) localServer.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
