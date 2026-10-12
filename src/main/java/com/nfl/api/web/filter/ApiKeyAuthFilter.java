package com.nfl.api.web.filter;

import com.nfl.api.domain.ApiKey;
import com.nfl.api.domain.enums.ApiKeyRole;
import com.nfl.api.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Autenticação por chave de API (header X-API-Key) com níveis de acesso:
 * <table>
 *   <tr><td>GET /api, POST /api/keys/register</td><td>público</td></tr>
 *   <tr><td>GET (demais)</td><td>READ_ONLY</td></tr>
 *   <tr><td>POST, PUT, PATCH</td><td>READ_WRITE</td></tr>
 *   <tr><td>DELETE e /api/keys/**</td><td>ADMIN</td></tr>
 * </table>
 * Sem chave ou chave inválida/revogada → 401. Nível insuficiente → 403.
 */
@Component
@Order(2)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    public static final String CURRENT_API_KEY = "nfl.currentApiKey";

    private final ApiKeyService apiKeyService;
    private final ErrorResponseWriter errorWriter;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService, ErrorResponseWriter errorWriter) {
        this.apiKeyService = apiKeyService;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        return !uri.startsWith("/api")
                || "OPTIONS".equals(method)
                || uri.equals("/api") || uri.equals("/api/")
                || ("POST".equals(method) && uri.equals("/api/keys/register"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String rawKey = request.getHeader(HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            response.setHeader("WWW-Authenticate", "ApiKey header=\"" + HEADER + "\"");
            errorWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                    "Chave de API ausente. Envie o header " + HEADER
                            + " (obtenha uma em POST /api/keys/register).");
            return;
        }

        Optional<ApiKey> apiKey = apiKeyService.authenticate(rawKey.trim());
        if (apiKey.isEmpty()) {
            errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "Chave de API inválida ou revogada.");
            return;
        }

        ApiKeyRole required = requiredRole(request);
        ApiKey key = apiKey.get();
        if (!key.getRole().includes(required)) {
            errorWriter.write(request, response, HttpStatus.FORBIDDEN,
                    "Sua chave tem nível " + key.getRole() + "; esta operação exige " + required + ".");
            return;
        }

        request.setAttribute(CURRENT_API_KEY, key);
        chain.doFilter(request, response);
    }

    private ApiKeyRole requiredRole(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if (uri.equals("/api/keys/me")) {
            return ApiKeyRole.READ_ONLY;
        }
        if (uri.startsWith("/api/keys") || "DELETE".equals(method)) {
            return ApiKeyRole.ADMIN;
        }
        if ("GET".equals(method) || "HEAD".equals(method)) {
            return ApiKeyRole.READ_ONLY;
        }
        return ApiKeyRole.READ_WRITE;
    }
}
