package com.erp.insurance.integration.anomaly;

import com.erp.grpc.anomaly.v1.AnalyzeRequest;
import com.erp.grpc.anomaly.v1.AnalyzeResponse;

public interface AnomalyDetector {
    Detection analyze(AnalyzeRequest request, String correlationId);

    enum Mode { REMOTE, IN_PROCESS }

    record Detection(AnalyzeResponse response, Mode mode) {}
}
