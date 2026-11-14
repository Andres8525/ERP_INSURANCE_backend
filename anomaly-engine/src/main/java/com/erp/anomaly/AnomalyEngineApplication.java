package com.erp.anomaly;

import com.erp.anomaly.engine.AnomalyEngine;
import com.erp.anomaly.grpc.AnomalyEngineServices;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.HealthStatusManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class AnomalyEngineApplication {
    private static final System.Logger LOG = System.getLogger(AnomalyEngineApplication.class.getName());
    private static final int DEFAULT_PORT = 9090;

    private AnomalyEngineApplication() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = port(System.getenv("GRPC_PORT"));
        ServerBuilder<?> builder = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                .maxInboundMessageSize(16 * 1024 * 1024)
                .keepAliveTime(60, TimeUnit.SECONDS);
        HealthStatusManager health = AnomalyEngineServices.registerWithOperations(builder, AnomalyEngine.withDefaultRules());
        Server server = builder.build().start();
        LOG.log(System.Logger.Level.INFO, "{0} escuchando gRPC en el puerto {1}", AnomalyEngine.VERSION, port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            health.enterTerminalState();
            server.shutdown();
            try {
                if (!server.awaitTermination(10, TimeUnit.SECONDS)) server.shutdownNow();
            } catch (InterruptedException exception) {
                server.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }, "anomaly-engine-shutdown"));
        server.awaitTermination();
    }

    static int port(String value) {
        if (value == null || value.isBlank()) return DEFAULT_PORT;
        int port = Integer.parseInt(value.trim());
        if (port < 1 || port > 65_535) throw new IllegalArgumentException("GRPC_PORT out of range: " + port);
        return port;
    }
}
