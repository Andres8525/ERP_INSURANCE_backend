package com.erp.insurance.audit;

import com.erp.insurance.domain.DomainEnums.AnomalyResolutionStatus;
import com.erp.insurance.integration.ai.AiAssistant;
import com.erp.insurance.integration.ai.AiAssistant.AiCompletion;
import com.erp.insurance.integration.ai.AiAssistant.AiPrompt;
import com.erp.insurance.integration.ai.AiProperties;
import com.erp.insurance.integration.ai.AiUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;
import java.util.UUID;

import static com.erp.insurance.audit.AuditDtos.AiInsightResponse;
import static com.erp.insurance.audit.AuditDtos.AiReport;
import static com.erp.insurance.audit.AuditDtos.AiStatusResponse;

@Service
public class AiInsightService {
    private final AiAssistant assistant;
    private final AiProperties properties;
    private final DataAnomalyRepository anomalies;
    private final TransactionTemplate readOnly;
    private final Clock clock;
    private final Map<String, CachedInsight> cache = new ConcurrentHashMap<>();

    @Autowired
    public AiInsightService(AiAssistant assistant, AiProperties properties, DataAnomalyRepository anomalies,
                            PlatformTransactionManager transactions) {
        this(assistant, properties, anomalies, transactions, Clock.systemUTC());
    }

    AiInsightService(AiAssistant assistant, AiProperties properties, DataAnomalyRepository anomalies,
                     PlatformTransactionManager transactions, Clock clock) {
        this.clock = clock;
        this.assistant = assistant;
        this.properties = properties;
        this.anomalies = anomalies;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
    }

    public AiStatusResponse status() {
        return new AiStatusResponse(assistant.available(), assistant.provider(), assistant.model());
    }

    public AiInsightResponse insights(boolean refresh) {
        Snapshot snapshot = Objects.requireNonNull(readOnly.execute(status -> {
            Page<DataAnomalyEntity> open = anomalies.findByResolutionStatus(AnomalyResolutionStatus.OPEN,
                    PageRequest.of(0, Math.max(1, properties.maxAnomalies()), Sort.by(Sort.Direction.DESC, "createdAt")));
            return new Snapshot(AiPromptFactory.insights(open.getContent(), open.getTotalElements()),
                    open.getNumberOfElements());
        }));
        if (snapshot.analyzed() == 0) {
            AiReport empty = new AiReport("No hay anomalías abiertas: la bandeja de auditoría está al día.", null,
                    "BAJO", List.of(), List.of(), List.of());
            return new AiInsightResponse(AiReportParser.toMarkdown(empty), empty, assistant.provider(),
                    assistant.model(), 0, 0, 0, 0, clock.instant(), false);
        }
        return cachedOrFresh(snapshot.prompt(), snapshot.analyzed(), refresh);
    }

    public AiInsightResponse explain(UUID id) {
        AiPrompt prompt = Objects.requireNonNull(readOnly.execute(status -> anomalies.findById(id)
                .map(AiPromptFactory::explain)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anomaly not found"))));
        return cachedOrFresh(prompt, 1, false);
    }

    private AiInsightResponse cachedOrFresh(AiPrompt prompt, int analyzed, boolean refresh) {
        String key = assistant.model() + "|" + prompt.user();
        Instant now = clock.instant();
        cache.values().removeIf(entry -> entry.expiresAt().isBefore(now));
        CachedInsight hit = cache.get(key);
        if (!refresh && hit != null) return hit.response().fromCache();
        AiInsightResponse fresh = respond(prompt, analyzed);
        Duration ttl = properties.cacheTtl();
        if (ttl != null && ttl.isPositive()) cache.put(key, new CachedInsight(fresh, now.plus(ttl)));
        return fresh;
    }

    private AiInsightResponse respond(AiPrompt prompt, int analyzed) {
        try {
            AiCompletion completion = assistant.complete(prompt);
            AiReport report = prompt.structured() ? AiReportParser.parse(completion.content()).orElse(null) : null;
            String content = report == null ? completion.content() : AiReportParser.toMarkdown(report);
            return new AiInsightResponse(content, report, assistant.provider(), completion.model(), analyzed,
                    completion.promptTokens(), completion.completionTokens(), completion.latencyMs(), clock.instant(), false);
        } catch (AiUnavailableException exception) {
            HttpStatus status = switch (exception.reason()) {
                case NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
                case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
                case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
                case UNAUTHORIZED, NO_CREDITS, PROVIDER_ERROR -> HttpStatus.BAD_GATEWAY;
            };
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
            problem.setTitle("Asistente IA no disponible");
            problem.setProperty("reason", exception.reason().name());
            throw new ErrorResponseException(status, problem, exception);
        }
    }

    private record Snapshot(AiPrompt prompt, int analyzed) {}

    private record CachedInsight(AiInsightResponse response, Instant expiresAt) {}
}
