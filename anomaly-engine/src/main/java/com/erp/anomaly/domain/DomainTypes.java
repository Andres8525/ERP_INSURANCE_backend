package com.erp.anomaly.domain;

public final class DomainTypes {
    private DomainTypes() {}

    public enum PolicyState { PENDING, ACTIVE, TERMINATED }
    public enum FindingType { POLICY_OVERLAP, DUPLICATE, OBSOLETE_FILE, OTHER }
    public enum Severity { RED, YELLOW }
    public enum RecordType { PATIENT, POLICY }
}
