package com.erp.insurance.integration.anomaly;

import com.erp.anomaly.engine.AnomalyEngine;
import com.erp.anomaly.grpc.AnomalyEngineServices;
import com.erp.grpc.anomaly.v1.AnalyzeRequest;
import com.erp.grpc.anomaly.v1.PatientSnapshot;
import com.erp.insurance.integration.anomaly.AnomalyDetector.Detection;
import com.erp.insurance.integration.anomaly.AnomalyDetector.Mode;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcAnomalyDetectorTest {
    private static final AnalyzeRequest REQUEST = AnalyzeRequest.newBuilder()
            .setReferenceDate("2026-11-14")
            .addPatients(PatientSnapshot.newBuilder().setId("p1").setFirstName("Piedad").setLastName("Garcia")
                    .setEmail("piedad@gmailcom").setDateOfBirth("1969-03-10"))
            .build();

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static AnomalyEngineProperties remote(int port, boolean fallback) {
        return new AnomalyEngineProperties("localhost:" + port, false, Duration.ofSeconds(3), fallback);
    }

    @Test
    void withoutRemoteAddressTheEngineRunsInProcess() throws Exception {
        GrpcAnomalyDetector detector = new GrpcAnomalyDetector(new AnomalyEngineProperties(null, false, Duration.ofSeconds(3), true));
        try {
            Detection detection = detector.analyze(REQUEST, "test-1");
            assertThat(detection.mode()).isEqualTo(Mode.IN_PROCESS);
            assertThat(detection.response().getFindings(0).getSuggestedValue()).isEqualTo("piedad@gmail.com");
        } finally {
            detector.destroy();
        }
    }

    @Test
    void remoteMicroserviceIsUsedOverTheNetworkWhenAvailable() throws Exception {
        int port = freePort();
        Server server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                .addService(AnomalyEngineServices.engine(AnomalyEngine.withDefaultRules()))
                .build().start();
        GrpcAnomalyDetector detector = new GrpcAnomalyDetector(remote(port, true));
        try {
            Detection detection = detector.analyze(REQUEST, "test-2");
            assertThat(detection.mode()).isEqualTo(Mode.REMOTE);
            assertThat(detection.response().getFindingsCount()).isEqualTo(1);
        } finally {
            detector.destroy();
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void unreachableMicroserviceFallsBackToTheInProcessEngine() throws Exception {
        GrpcAnomalyDetector detector = new GrpcAnomalyDetector(remote(freePort(), true));
        try {
            assertThat(detector.analyze(REQUEST, "test-3").mode()).isEqualTo(Mode.IN_PROCESS);
        } finally {
            detector.destroy();
        }
    }

    @Test
    void fallbackCanBeDisabledToSurfaceTheOutage() throws Exception {
        GrpcAnomalyDetector detector = new GrpcAnomalyDetector(remote(freePort(), false));
        try {
            assertThatThrownBy(() -> detector.analyze(REQUEST, "test-4"))
                    .isInstanceOfSatisfying(StatusRuntimeException.class,
                            exception -> assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
        } finally {
            detector.destroy();
        }
    }
}
