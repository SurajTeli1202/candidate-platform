package com.suraj.candidates.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final String HEADER = "X-Request-Id";
    private static final Pattern VALID_UUID =
            Pattern.compile("[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String suppliedId = request.getHeader(HEADER);
        String requestId = suppliedId != null && VALID_UUID.matcher(suppliedId).matches()
                ? suppliedId : UUID.randomUUID().toString();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        long start = System.nanoTime();
        boolean completed = false;
        MDC.put("requestId", requestId);
        try {
            response.setHeader(HEADER, requestId);
            filterChain.doFilter(request, response);
            completed = true;
        } finally {
            try {
                if (shouldLog(path)) {
                    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                    String route = pattern == null ? "unmatched" : pattern.toString();
                    log.info("HTTP {} {} status={} durationMs={}", request.getMethod(), route,
                            completed ? response.getStatus() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                }
            } finally {
                MDC.remove("requestId");
            }
        }
    }

    private boolean shouldLog(String path) {
        return !path.equals("/actuator") && !path.startsWith("/actuator/")
                && !path.equals("/swagger-ui.html") && !path.equals("/swagger-ui")
                && !path.startsWith("/swagger-ui/") && !path.startsWith("/webjars/swagger-ui/")
                && !path.equals("/v3/api-docs") && !path.startsWith("/v3/api-docs/")
                && !path.equals("/v3/api-docs.yaml");
    }
}
