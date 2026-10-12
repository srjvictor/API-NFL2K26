package com.nfl.api.web.filter;

import com.nfl.api.config.AppProperties;
import com.nfl.api.domain.IdempotencyRecord;
import com.nfl.api.domain.enums.IdempotencyStatus;
import com.nfl.api.repository.IdempotencyRecordRepository;
import com.nfl.api.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Idempotência para requisições POST em /api/**.
 * <ol>
 *   <li>O header X-Idempotency-Key é obrigatório (400 se ausente).</li>
 *   <li>Primeira requisição com a chave: é processada e, se bem-sucedida (2xx), a resposta é armazenada.</li>
 *   <li>Repetição com a mesma chave e o mesmo corpo: a resposta original é devolvida sem reprocessar,
 *       com o header Idempotent-Replayed: true.</li>
 *   <li>Mesma chave com corpo diferente: 422. Mesma chave ainda em processamento: 409.</li>
 *   <li>Respostas de erro não são armazenadas — o cliente pode corrigir e tentar de novo com a mesma chave.</li>
 * </ol>
 * As chaves são isoladas por cliente (chave de API ou IP) e expiram após o TTL configurado.
 */
@Component
@Order(3)
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Idempotency-Key";

    private final IdempotencyRecordRepository repository;
    private final ErrorResponseWriter errorWriter;
    private final Duration ttl;

    public IdempotencyFilter(IdempotencyRecordRepository repository, ErrorResponseWriter errorWriter,
                             AppProperties properties) {
        this.repository = repository;
        this.errorWriter = errorWriter;
        this.ttl = Duration.ofHours(properties.idempotency().ttlHours());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            errorWriter.write(request, response, HttpStatus.BAD_REQUEST,
                    "O header " + HEADER + " é obrigatório em requisições POST (ex.: um UUID).");
            return;
        }
        key = key.trim();
        if (key.length() > 100) {
            errorWriter.write(request, response, HttpStatus.BAD_REQUEST,
                    "O header " + HEADER + " deve ter no máximo 100 caracteres.");
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String clientId = clientId(request);
        String requestHash = ApiKeyService.sha256(request.getMethod() + " " + request.getRequestURI() + "\n"
                + new String(cachedRequest.getBody(), StandardCharsets.UTF_8));

        Optional<IdempotencyRecord> existing = repository.findByClientIdAndIdempotencyKey(clientId, key);
        if (existing.isPresent() && existing.get().isExpired()) {
            repository.delete(existing.get());
            existing = Optional.empty();
        }

        if (existing.isPresent()) {
            handleRepeated(existing.get(), requestHash, request, response);
            return;
        }

        IdempotencyRecord record = new IdempotencyRecord();
        record.setClientId(clientId);
        record.setIdempotencyKey(key);
        record.setRequestHash(requestHash);
        record.setStatus(IdempotencyStatus.IN_PROGRESS);
        record.setCreatedAt(Instant.now());
        record.setExpiresAt(Instant.now().plus(ttl));
        try {
            record = repository.saveAndFlush(record);
        } catch (DataIntegrityViolationException race) {
            // Outra requisição simultânea com a mesma chave acabou de registrá-la
            errorWriter.write(request, response, HttpStatus.CONFLICT,
                    "Já existe uma requisição em processamento com esta " + HEADER + ".");
            return;
        }

        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        boolean stored = false;
        try {
            chain.doFilter(cachedRequest, cachedResponse);
            int status = cachedResponse.getStatus();
            if (status >= 200 && status < 300) {
                record.setStatus(IdempotencyStatus.COMPLETED);
                record.setResponseStatus(status);
                record.setResponseBody(new String(cachedResponse.getContentAsByteArray(), StandardCharsets.UTF_8));
                record.setContentType(cachedResponse.getContentType());
                record.setLocation(cachedResponse.getHeader("Location"));
                repository.save(record);
                stored = true;
            }
        } finally {
            if (!stored) {
                repository.delete(record);
            }
            cachedResponse.copyBodyToResponse();
        }
    }

    private void handleRepeated(IdempotencyRecord record, String requestHash, HttpServletRequest request,
                                HttpServletResponse response) throws IOException {
        if (!record.getRequestHash().equals(requestHash)) {
            errorWriter.write(request, response, HttpStatus.UNPROCESSABLE_CONTENT,
                    "Esta " + HEADER + " já foi usada com uma requisição diferente. Gere uma nova chave.");
            return;
        }
        if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
            errorWriter.write(request, response, HttpStatus.CONFLICT,
                    "A requisição original com esta " + HEADER + " ainda está em processamento.");
            return;
        }
        // Devolve a resposta original sem executar a operação novamente
        response.setStatus(record.getResponseStatus());
        if (record.getContentType() != null) {
            response.setContentType(record.getContentType());
        }
        if (record.getLocation() != null) {
            response.setHeader("Location", record.getLocation());
        }
        response.setHeader("Idempotent-Replayed", "true");
        byte[] body = record.getResponseBody() != null
                ? record.getResponseBody().getBytes(StandardCharsets.UTF_8) : new byte[0];
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    /** Escopo da chave: a chave de API (hash) ou, para rotas públicas, o IP do cliente. */
    private String clientId(HttpServletRequest request) {
        String apiKey = request.getHeader(ApiKeyAuthFilter.HEADER);
        String raw = (apiKey != null && !apiKey.isBlank()) ? "key:" + apiKey.trim() : "ip:" + request.getRemoteAddr();
        return ApiKeyService.sha256(raw);
    }
}
