package com.nfl.api.config;

import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Substitui o transformador padrão do springdoc pelo que aplica o tema NFL ao Swagger UI. */
@Configuration
public class SwaggerUiThemeConfig {

    @Bean
    public SwaggerIndexTransformer nflSwaggerIndexTransformer(SwaggerUiConfigProperties swaggerUiConfig,
                                                              SwaggerUiOAuthProperties swaggerUiOAuthProperties,
                                                              SwaggerWelcomeCommon swaggerWelcomeCommon,
                                                              ObjectMapperProvider objectMapperProvider) {
        return new NflSwaggerIndexTransformer(swaggerUiConfig, swaggerUiOAuthProperties,
                swaggerWelcomeCommon, objectMapperProvider);
    }
}
