package com.erp.insurance.integration.ai;

public class AiUnavailableException extends RuntimeException {
    private final Reason reason;

    public AiUnavailableException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AiUnavailableException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason { NOT_CONFIGURED, UNAUTHORIZED, NO_CREDITS, RATE_LIMITED, TIMEOUT, PROVIDER_ERROR }
}
