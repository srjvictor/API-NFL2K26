package com.nfl.api.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Personaliza a página do Swagger UI com o tema NFL: injeta o CSS temático
 * (static/nfl/nfl-swagger.css), o favicon e um cabeçalho com o logo da API.
 * Os demais arquivos do Swagger seguem o processamento padrão do springdoc.
 */
public class NflSwaggerIndexTransformer extends SwaggerIndexPageTransformer {

    private static final String HEADER = """
            <header class="nfl-header">
              <div class="nfl-header__inner">
                <img class="nfl-header__logo" src="/nfl/nfl-logo.svg" alt="Logo NFL API" />
                <div class="nfl-header__text">
                  <span class="nfl-header__title">NFL <span>API</span></span>
                  <span class="nfl-header__subtitle">Times, jogadores, estádios, patrocinadores e partidas da liga</span>
                </div>
                <nav class="nfl-header__links">
                  <a href="/api" target="_blank" rel="noopener">Raiz HATEOAS</a>
                  <a href="/h2-console" target="_blank" rel="noopener">Console H2</a>
                </nav>
              </div>
              <div class="nfl-yardlines" aria-hidden="true"></div>
            </header>
            """;

    public NflSwaggerIndexTransformer(SwaggerUiConfigProperties swaggerUiConfig,
                                      SwaggerUiOAuthProperties swaggerUiOAuthProperties,
                                      SwaggerWelcomeCommon swaggerWelcomeCommon,
                                      ObjectMapperProvider objectMapperProvider) {
        super(swaggerUiConfig, swaggerUiOAuthProperties, swaggerWelcomeCommon, objectMapperProvider);
    }

    @Override
    public Resource transform(HttpServletRequest request, Resource resource, ResourceTransformerChain chain)
            throws IOException {
        if (!"index.html".equals(resource.getFilename())) {
            return super.transform(request, resource, chain);
        }
        String html = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("<title>Swagger UI</title>", "<title>NFL API · Documentação</title>")
                .replaceAll("<link rel=\"icon\"[^>]*>\\s*", "")
                .replace("</head>", """
                            <link rel="icon" type="image/svg+xml" href="/nfl/nfl-logo.svg" />
                            <link rel="stylesheet" type="text/css" href="/nfl/nfl-swagger.css" />
                          </head>""")
                .replace("<div id=\"swagger-ui\"></div>", HEADER + "<div id=\"swagger-ui\"></div>");
        return new TransformedResource(resource, html.getBytes(StandardCharsets.UTF_8));
    }
}
