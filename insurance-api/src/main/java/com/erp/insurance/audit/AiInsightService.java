package com.erp.insurance.audit;

import com.erp.insurance.domain.DomainEnums.AnomalyResolutionStatus;
import com.erp.insurance.integration.ai.AiAssistant;
import com.erp.insurance.integration.ai.AiAssistant.AiCompletion;
import com.erp.insurance.integration.ai.AiAssistant.AiPrompt;
import com.erp.insurance.integration.ai.AiProperties;
import com.erp.insurance.integration.ai.AiUnavailableException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static com.erp.insurance.audit.AuditDtos.AiInsightResponse;
import static com.erp.insurance.audit.AuditDtos.AiStatusResponse;

@Service
public class AiInsightService {
    private final AiAssistant assistant;
    private final AiProperties properties;
    private final DataAnomalyRepository anomalies;
    private final TransactionTemplate readOnly;

    public AiInsightService(AiAssistant assistant, AiProperties properties, DataAnomalyRepository anomalies,
                            PlatformTransactionManager transactions) {
        this.assistant = assistant;
        this.properties = properties;
        this.anomalies = anomalies;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
    }

    public AiStatusResponse status() {
        return new AiStatusResponse(assistant.available(), assistant.provider(), assistant.model());
    }

    public AiInsightResponse insights() {
        Snapshot snapshot = Objects.requireNonNull(readOnly.execute(status -> {
            Page<DataAnomalyEntity> open = anomalies.findByResolutionStatus(AnomalyResolutionStatus.OPEN,
                    PageRequest.of(0, Math.max(1, properties.maxAnomalies()), Sort.by(Sort.Direction.DESC, "createdAt")));
            return new Snapshot(AiPromptFactory.insights(open.getContent(), open.getTotalElements()),
                    open.getNumberOfElements());
        }));
        if (snapshot.analyzed() == 0) {
            return new AiInsightResponse("No hay anomalías abiertas: la bandeja de auditoría está al día.",
                    assistant.provider(), assistant.model(), 0, 0, 0, 0, Instant.now());
        }
        return respond(snapshot.prompt(), snapshot.analyzed());
    }

    public AiInsightResponse explain(UUID id) {
        AiPrompt prompt = Objects.requireNonNull(readOnly.execute(status -> anomalies.findById(id)
                .map(AiPromptFactory::explain)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anomaly not found"))));
        return respond(prompt, 1);
    }

    private AiInsightResponse respond(AiPrompt prompt, int analyzed) {
        try {
            AiCompletion completion = assistant.complete(prompt);
            return new AiInsightResponse(completion.content(), assistant.provider(), completion.model(), analyzed,
                    completion.promptTokens(), completion.completionTokens(), completion.latencyMs(), Instant.now());
        } catch (AiUnavailableException exception) {
            HttpStatus status = switch (exception.reason()) {
                case NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
                case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
                case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
                case UNAUTHORIZED, PROVIDER_ERROR -> HttpStatus.BAD_GATEWAY;
            };
            throw new ResponseStatusException(status, exception.getMessage(), exception);
        }
    }

    private record Snapshot(AiPrompt prompt, int analyzed) {}
}
