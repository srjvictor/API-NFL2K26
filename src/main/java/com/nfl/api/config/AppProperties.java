package com.nfl.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Configurações próprias da aplicação (prefixo "nfl" no application.properties). */
@ConfigurationProperties(prefix = "nfl")
public record AppProperties(Security security, RateLimit rateLimit, Cors cors, Idempotency idempotency) {

    /** @param adminKey chave ADMIN criada na inicialização (em produção vem de NFL_ADMIN_KEY) */
    public record Security(String adminKey) {
        public Security {
            // Um placeholder não resolvido (ex.: variável de ambiente ausente) chega aqui como "${...}"
            if (adminKey == null || adminKey.isBlank() || adminKey.contains("${")) {
                throw new IllegalStateException(
                        "Chave ADMIN não configurada: defina a variável de ambiente NFL_ADMIN_KEY");
            }
            if (adminKey.length() < 16) {
                throw new IllegalStateException("A chave ADMIN (NFL_ADMIN_KEY) deve ter pelo menos 16 caracteres");
            }
        }
    }

    /** @param capacity requisições permitidas por janela; @param windowSeconds duração da janela */
    public record RateLimit(int capacity, long windowSeconds) {
    }

    public record Cors(List<String> allowedOrigins, List<String> allowedMethods, long maxAgeSeconds) {
    }

    /** @param ttlHours por quanto tempo uma chave idempotente fica registrada */
    public record Idempotency(long ttlHours) {
    }
}
