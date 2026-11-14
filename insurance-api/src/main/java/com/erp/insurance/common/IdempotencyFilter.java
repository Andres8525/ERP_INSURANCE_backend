package com.erp.insurance.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class IdempotencyFilter extends OncePerRequestFilter {
    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    private static final Set<String> METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Pattern VALID_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,128}$");

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;

    public IdempotencyFilter(Duration ttl, int maxEntries, Clock clock) {
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    private record Entry(Instant createdAt, boolean completed, int status, String contentType, byte[] body) {}

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !METHODS.contains(request.getMethod())
                || request.getHeader(HEADER) == null
                || !request.getRequestURI().startsWith("/api/")
                || request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER).trim();
        if (!VALID_KEY.matcher(key).matches()) {
            writeProblem(response, HttpStatus.BAD_REQUEST, "Idempotency-Key debe tener entre 8 y 128 caracteres [A-Za-z0-9_-]");
            return;
        }
        Instant now = clock.instant();
        evict(now);
        String scope = principal() + "|" + request.getMethod() + "|" + request.getRequestURI() + "|" + key;
        Entry inProgress = new Entry(now, false, 0, null, null);
        Entry existing = entries.putIfAbsent(scope, inProgress);
        if (existing != null) {
            if (!existing.completed()) {
                writeProblem(response, HttpStatus.CONFLICT, "Ya hay una solicitud en curso con este Idempotency-Key");
                return;
            }
            response.setStatus(existing.status());
            if (existing.contentType() != null) response.setContentType(existing.contentType());
            response.setHeader(REPLAYED_HEADER, "true");
            response.getOutputStream().write(existing.body());
            return;
        }

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapper);
        } catch (IOException | ServletException | RuntimeException exception) {
            entries.remove(scope);
            throw exception;
        }
        int status = wrapper.getStatus();
        if (status >= 500) {
            entries.remove(scope);
        } else {
            entries.put(scope, new Entry(now, true, status, wrapper.getContentType(), wrapper.getContentAsByteArray()));
        }
        wrapper.copyBodyToResponse();
    }

    int size() {
        return entries.size();
    }

    private void evict(Instant now) {
        Instant limit = now.minus(ttl);
        entries.values().removeIf(entry -> entry.createdAt().isBefore(limit));
        if (entries.size() < maxEntries) return;
        Iterator<String> keys = entries.keySet().iterator();
        while (entries.size() >= maxEntries && keys.hasNext()) {
            keys.next();
            keys.remove();
        }
    }

    private static String principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "anonymous" : authentication.getName();
    }

    private static void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status.value() + ",\"title\":\"" + status.getReasonPhrase()
                + "\",\"detail\":\"" + detail.replace("\"", "'") + "\"}");
    }
}
