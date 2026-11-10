package com.nfl.api.web.filter;

import com.nfl.api.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Descobre o IP do cliente para o rate limit e para o escopo da idempotência.
 * <p>
 * Atrás de um proxy (Render, Railway...) o IP da conexão é o do próprio proxy, então usá-lo faria todos os
 * clientes dividirem o mesmo contador. O IP real vem em um header que o proxy define
 * (nfl.rate-limit.client-ip-header). O X-Forwarded-For não serve: o proxy acrescenta ao valor enviado
 * pelo cliente, que pode forjá-lo livremente para escapar do limite.
 */
@Component
public class ClientIpResolver {

    /** Aceita apenas algo com cara de IPv4/IPv6; evita lixo forjado virar chave de mapa. */
    private static final Pattern IP_LIKE = Pattern.compile("^[0-9a-fA-F:.]{3,45}$");

    private final String header;

    public ClientIpResolver(AppProperties properties) {
        String configured = properties.rateLimit().clientIpHeader();
        this.header = (configured == null || configured.isBlank()) ? null : configured.trim();
    }

    public String resolve(HttpServletRequest request) {
        if (header != null) {
            String value = request.getHeader(header);
            if (value != null) {
                String first = value.split(",")[0].trim();
                if (IP_LIKE.matcher(first).matches()) {
                    return first;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
