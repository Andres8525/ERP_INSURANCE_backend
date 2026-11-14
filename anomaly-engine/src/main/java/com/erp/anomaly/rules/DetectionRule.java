package com.erp.anomaly.rules;

import com.erp.anomaly.domain.Dataset;
import com.erp.anomaly.domain.Finding;

import java.util.stream.Stream;

public interface DetectionRule {
    String id();

    Stream<Finding> evaluate(Dataset dataset);
}
