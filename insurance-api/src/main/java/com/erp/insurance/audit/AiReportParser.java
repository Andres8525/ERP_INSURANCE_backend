package com.erp.insurance.audit;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.erp.insurance.audit.AuditDtos.AiAction;
import static com.erp.insurance.audit.AuditDtos.AiPriority;
import static com.erp.insurance.audit.AuditDtos.AiReport;

final class AiReportParser {
    static final int MAX_PRIORITIES = 6;
    static final int MAX_ACTIONS = 6;
    static final int MAX_PREVENTION = 5;
    private static final int MAX_TEXT = 400;
    private static final Pattern HTML_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]{1,40}>");
    private static final Pattern MARKDOWN_EMPHASIS = Pattern.compile("\\*\\*|__|`");
    private static final ObjectMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private AiReportParser() {}

    static Optional<AiReport> parse(String content) {
        if (content == null) return Optional.empty();
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) return Optional.empty();
        JsonNode root;
        try {
            root = JSON.readTree(content.substring(start, end + 1));
        } catch (Exception exception) {
            return Optional.empty();
        }
        String summary = text(root, "summary");
        if (summary == null) return Optional.empty();
        AiReport report = new AiReport(summary, text(root, "impact"), riskLevel(text(root, "riskLevel")),
                list(root, "priorities", MAX_PRIORITIES, AiReportParser::priority),
                list(root, "actions", MAX_ACTIONS, AiReportParser::action),
                list(root, "prevention", MAX_PREVENTION, node -> clean(node.isTextual() ? node.asText() : text(node, "detail"))));
        return Optional.of(report);
    }

    static String toMarkdown(AiReport report) {
        StringBuilder markdown = new StringBuilder("## Resumen\n").append(report.summary()).append('\n');
        if (report.impact() != null) markdown.append("\n## Por qué importa\n").append(report.impact()).append('\n');
        if (!report.priorities().isEmpty()) {
            markdown.append("\n## Prioridades\n");
            int rank = 1;
            for (AiPriority priority : report.priorities()) {
                markdown.append(rank++).append(". **").append(priority.title()).append("**");
                if (priority.severity() != null) markdown.append(" (").append(priority.severity())
                        .append(priority.count() == null ? "" : ", " + priority.count() + " caso(s)").append(')');
                if (priority.risk() != null) markdown.append(": ").append(priority.risk());
                if (priority.action() != null) markdown.append(" Acción: ").append(priority.action());
                markdown.append('\n');
            }
        }
        if (!report.actions().isEmpty()) {
            markdown.append("\n## Acciones recomendadas\n");
            for (AiAction action : report.actions()) {
                markdown.append("- **").append(action.title()).append("**");
                if (action.owner() != null) markdown.append(" (").append(action.owner()).append(')');
                if (action.detail() != null) markdown.append(": ").append(action.detail());
                markdown.append('\n');
            }
        }
        if (!report.prevention().isEmpty()) {
            markdown.append("\n## Prevención\n");
            report.prevention().forEach(item -> markdown.append("- ").append(item).append('\n'));
        }
        return markdown.toString().strip();
    }

    private static AiPriority priority(JsonNode node) {
        String title = text(node, "title");
        if (title == null) return null;
        Integer count = node.path("count").canConvertToInt() && node.path("count").asInt() > 0 ? node.path("count").asInt() : null;
        return new AiPriority(title, severity(text(node, "severity")), text(node, "recordType"), count,
                text(node, "risk"), text(node, "action"));
    }

    private static AiAction action(JsonNode node) {
        if (node.isTextual()) {
            String title = clean(node.asText());
            return title == null ? null : new AiAction(title, null, null);
        }
        String title = text(node, "title");
        return title == null ? null : new AiAction(title, text(node, "owner"), text(node, "detail"));
    }

    private static <T> List<T> list(JsonNode root, String field, int max, Function<JsonNode, T> mapper) {
        List<T> items = new ArrayList<>();
        JsonNode array = root.path(field);
        if (!array.isArray()) return items;
        for (JsonNode node : array) {
            T item = mapper.apply(node);
            if (item != null) items.add(item);
            if (items.size() == max) break;
        }
        return List.copyOf(items);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() ? clean(value.asText()) : null;
    }

    static String clean(String raw) {
        if (raw == null) return null;
        String text = HTML_BREAK.matcher(raw).replaceAll(" ");
        text = HTML_TAG.matcher(text).replaceAll("");
        text = MARKDOWN_EMPHASIS.matcher(text).replaceAll("");
        text = text.replaceAll("\\s+", " ").strip();
        if (text.isEmpty() || text.equalsIgnoreCase("null")) return null;
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT - 1).strip() + "…" : text;
    }

    static String riskLevel(String raw) {
        if (raw == null) return "MEDIO";
        String value = raw.toUpperCase(Locale.ROOT);
        if (value.startsWith("ALT") || value.startsWith("HIGH") || value.startsWith("CRÍT") || value.startsWith("CRIT")) return "ALTO";
        if (value.startsWith("BAJ") || value.startsWith("LOW")) return "BAJO";
        return "MEDIO";
    }

    static String severity(String raw) {
        if (raw == null) return null;
        String value = raw.toLowerCase(Locale.ROOT);
        if (value.startsWith("crít") || value.startsWith("crit") || value.equals("red") || value.startsWith("alta")) return "Crítica";
        if (value.startsWith("rev") || value.equals("yellow") || value.startsWith("media") || value.startsWith("baja")) return "Revisión";
        if (value.startsWith("mixt")) return "Mixta";
        return null;
    }
}
