package com.erp.anomaly.grpc;

import com.erp.anomaly.engine.AnomalyEngine;
import com.erp.grpc.GrpcMetadata;
import com.erp.grpc.anomaly.v1.AnalyzeRequest;
import com.erp.grpc.anomaly.v1.AnalyzeResponse;
import com.erp.grpc.anomaly.v1.AnomalyEngineGrpc;
import com.erp.grpc.anomaly.v1.Finding;
import com.erp.grpc.anomaly.v1.FindingType;
import com.erp.grpc.anomaly.v1.PatientSnapshot;
import com.erp.grpc.anomaly.v1.PolicySnapshot;
import com.erp.grpc.anomaly.v1.PolicyStatus;
import com.erp.grpc.anomaly.v1.Severity;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnomalyEngineGrpcServiceTest {
    private Server server;
    private ManagedChannel channel;
    private AnomalyEngineGrpc.AnomalyEngineBlockingStub stub;

    @BeforeEach
    void start() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(AnomalyEngineServices.engine(AnomalyEngine.withDefaultRules()))
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        stub = AnomalyEngineGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void stop() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private static AnalyzeRequest sampleRequest() {
        return AnalyzeRequest.newBuilder()
                .setReferenceDate("2026-11-14")
                .addPatients(PatientSnapshot.newBuilder().setId("p1").setFirstName("Piedad").setLastName("Garcia")
                        .setEmail("piedad@gmailcom").setPhone("3054508353").setDateOfBirth("1969-03-10"))
                .addPatients(PatientSnapshot.newBuilder().setId("p2").setFirstName("Ana").setLastName("Ruiz")
                        .setEmail("ana@correo.com").setPhone("(305) 555-0100").setDateOfBirth("1980-01-01"))
                .addPolicies(PolicySnapshot.newBuilder().setId("pol1").setPatientId("p2").setInsurer("Oscar Health")
                        .setPlanName("Gold ACA").setCoverageStart("2026-01-01").setCoverageEnd("2026-12-31")
                        .setPremiumAmount("0").setStatus(PolicyStatus.POLICY_STATUS_ACTIVE))
                .build();
    }

    @Test
    void unaryAnalyzeReturnsFindingsSortedBySeverity() {
        AnalyzeResponse response = stub.analyze(sampleRequest());

        assertThat(response.getAnalyzedRecords()).isEqualTo(3);
        assertThat(response.getEngineVersion()).isEqualTo(AnomalyEngine.VERSION);
        assertThat(response.getAppliedRulesList()).contains("email-format", "policy-overlap");
        assertThat(response.getFindingsList()).extracting(Finding::getRule)
                .containsExactly("premium-range", "email-format", "phone-format");
        assertThat(response.getFindings(0).getSeverity()).isEqualTo(Severity.SEVERITY_RED);
        assertThat(response.getFindings(1).getSuggestedValue()).isEqualTo("piedad@gmail.com");
        assertThat(response.getFindings(2).getType()).isEqualTo(FindingType.FINDING_TYPE_OTHER);
    }

    @Test
    void serverStreamingEmitsEveryFinding() {
        List<Finding> streamed = new ArrayList<>();
        stub.analyzeStream(sampleRequest()).forEachRemaining(streamed::add);

        assertThat(streamed).hasSize(3);
    }

    @Test
    void malformedSnapshotsAreRejectedWithInvalidArgument() {
        AnalyzeRequest request = AnalyzeRequest.newBuilder()
                .addPatients(PatientSnapshot.newBuilder().setId("p1").setDateOfBirth("10/03/1969"))
                .build();

        assertThatThrownBy(() -> stub.analyze(request))
                .isInstanceOfSatisfying(StatusRuntimeException.class, exception -> {
                    assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(exception.getStatus().getDescription()).contains("patient.date_of_birth");
                });
    }

    @Test
    void correlationIdIsEchoedBackInResponseHeaders() {
        Metadata outgoing = new Metadata();
        outgoing.put(GrpcMetadata.CORRELATION_ID, "scan-123");
        AtomicReference<Metadata> headers = new AtomicReference<>();
        AtomicReference<Metadata> trailers = new AtomicReference<>();

        stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(outgoing),
                        MetadataUtils.newCaptureMetadataInterceptor(headers, trailers))
                .analyze(sampleRequest());

        assertThat(headers.get().get(GrpcMetadata.CORRELATION_ID)).isEqualTo("scan-123");
    }
}
