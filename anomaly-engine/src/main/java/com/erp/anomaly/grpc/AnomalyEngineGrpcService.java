package com.erp.anomaly.grpc;

import com.erp.anomaly.domain.Dataset;
import com.erp.anomaly.engine.AnomalyEngine;
import com.erp.grpc.anomaly.v1.AnalyzeRequest;
import com.erp.grpc.anomaly.v1.AnalyzeResponse;
import com.erp.grpc.anomaly.v1.AnomalyEngineGrpc;
import com.erp.grpc.anomaly.v1.Finding;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import java.time.Clock;
import java.util.Iterator;

public final class AnomalyEngineGrpcService extends AnomalyEngineGrpc.AnomalyEngineImplBase {
    static final int MAX_RECORDS = 50_000;

    private final AnomalyEngine engine;
    private final Clock clock;

    public AnomalyEngineGrpcService(AnomalyEngine engine, Clock clock) {
        this.engine = engine;
        this.clock = clock;
    }

    public AnomalyEngineGrpcService(AnomalyEngine engine) {
        this(engine, Clock.systemUTC());
    }

    @Override
    public void analyze(AnalyzeRequest request, StreamObserver<AnalyzeResponse> responseObserver) {
        Dataset dataset;
        try {
            dataset = dataset(request);
        } catch (IllegalArgumentException exception) {
            responseObserver.onError(invalid(exception));
            return;
        }
        responseObserver.onNext(ProtoMapper.toResponse(engine.analyze(dataset)));
        responseObserver.onCompleted();
    }

    @Override
    public void analyzeStream(AnalyzeRequest request, StreamObserver<Finding> responseObserver) {
        Dataset dataset;
        try {
            dataset = dataset(request);
        } catch (IllegalArgumentException exception) {
            responseObserver.onError(invalid(exception));
            return;
        }
        ServerCallStreamObserver<Finding> call = (ServerCallStreamObserver<Finding>) responseObserver;
        Iterator<com.erp.anomaly.domain.Finding> findings = engine.stream(dataset).iterator();
        while (findings.hasNext()) {
            if (call.isCancelled()) return;
            call.onNext(ProtoMapper.toProto(findings.next()));
        }
        call.onCompleted();
    }

    private Dataset dataset(AnalyzeRequest request) {
        if (request.getPatientsCount() + request.getPoliciesCount() > MAX_RECORDS) {
            throw new IllegalArgumentException("A single request accepts at most " + MAX_RECORDS + " records");
        }
        return ProtoMapper.toDataset(request, clock);
    }

    private static RuntimeException invalid(IllegalArgumentException exception) {
        return Status.INVALID_ARGUMENT.withDescription(exception.getMessage()).asRuntimeException();
    }
}
