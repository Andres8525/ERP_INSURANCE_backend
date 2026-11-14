package com.erp.anomaly.grpc;

import com.erp.anomaly.engine.AnomalyEngine;
import com.erp.grpc.anomaly.v1.AnomalyEngineGrpc;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;

public final class AnomalyEngineServices {
    private AnomalyEngineServices() {}

    public static ServerServiceDefinition engine(AnomalyEngine engine) {
        return ServerInterceptors.intercept(new AnomalyEngineGrpcService(engine), new CorrelationIdServerInterceptor());
    }

    public static HealthStatusManager registerWithOperations(ServerBuilder<?> builder, AnomalyEngine engine) {
        HealthStatusManager health = new HealthStatusManager();
        builder.addService(engine(engine));
        builder.addService(health.getHealthService());
        builder.addService(ProtoReflectionServiceV1.newInstance());
        health.setStatus(AnomalyEngineGrpc.SERVICE_NAME, ServingStatus.SERVING);
        health.setStatus(HealthStatusManager.SERVICE_NAME_ALL_SERVICES, ServingStatus.SERVING);
        return health;
    }
}
