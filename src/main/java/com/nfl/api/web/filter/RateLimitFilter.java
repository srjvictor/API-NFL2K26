package com.nfl.api.web.filter;

import com.nfl.api.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting por IP do cliente usando janela fixa.
 * <p>
 * Toda resposta de /api/** recebe os headers X-RateLimit-Limit, X-RateLimit-Remaining e
 * X-RateLimit-Reset. Ao exceder o limite a API responde 429 Too Many Requests com Retry-After.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final int capacity;
    private final long windowMillis;
    private final ErrorResponseWriter errorWriter;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties properties, ErrorResponseWriter errorWriter) {
        this.capacity = properties.rateLimit().capacity();
        this.windowMillis = properties.rateLimit().windowSeconds() * 1000;
        this.errorWriter = errorWriter;
    }

    /** Janela de contagem de um cliente. Imutável: cada requisição gera uma nova instância. */
    private record Window(long startMillis, int count) {
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api") || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        String client = request.getRemoteAddr();

        Window window = windows.compute(client, (k, w) ->
                (w == null || now - w.startMillis() >= windowMillis) ? new Window(now, 1) : new Window(w.startMillis(), w.count() + 1));

        long resetMillis = window.startMillis() + windowMillis;
        int remaining = Math.max(0, capacity - window.count());
        response.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(Instant.ofEpochMilli(resetMillis).getEpochSecond()));

        if (window.count() > capacity) {
            long retryAfter = Math.max(1, (resetMillis - now + 999) / 1000);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            errorWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de " + capacity + " requisições por " + (windowMillis / 1000)
                            + "s excedido. Tente novamente em " + retryAfter + "s.");
            return;
        }

        cleanupIfNeeded(now);
        chain.doFilter(request, response);
    }

    /** Evita crescimento indefinido do mapa removendo janelas expiradas. */
    private void cleanupIfNeeded(long now) {
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().startMillis() >= windowMillis);
        }
    }
}
