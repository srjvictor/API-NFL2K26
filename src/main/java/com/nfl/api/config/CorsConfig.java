package com.nfl.api.config;

import com.nfl.api.web.filter.ApiKeyAuthFilter;
import com.nfl.api.web.filter.IdempotencyFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Política de CORS. Registrada como o primeiro filtro da cadeia para que:
 * <ul>
 *   <li>requisições preflight (OPTIONS) sejam respondidas sem exigir X-API-Key;</li>
 *   <li>respostas de erro geradas pelos filtros (401, 429...) também levem os headers CORS.</li>
 * </ul>
 * Origens não listadas recebem 403 "Invalid CORS request".
 */
@Configuration
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(AppProperties properties) {
        AppProperties.Cors cors = properties.cors();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.allowedOrigins());
        config.setAllowedMethods(cors.allowedMethods());
        config.setAllowedHeaders(List.of("Content-Type", "Accept", ApiKeyAuthFilter.HEADER,
                IdempotencyFilter.HEADER, "X-API-Version"));
        config.setExposedHeaders(List.of("Location", "X-RateLimit-Limit", "X-RateLimit-Remaining",
                "X-RateLimit-Reset", "Retry-After", "Idempotent-Replayed", "X-API-Version", "Deprecation"));
        config.setAllowCredentials(false);
        config.setMaxAge(cors.maxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        // Logo depois do ForwardedHeaderFilter (HIGHEST_PRECEDENCE): atrás de um proxy, o CORS precisa
        // enxergar o host/esquema público para reconhecer requisições same-origin (ex.: o Swagger)
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }
}
