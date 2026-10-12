package com.nfl.api.config;

import com.nfl.api.web.filter.ApiKeyAuthFilter;
import com.nfl.api.web.filter.IdempotencyFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "ApiKeyAuth";
    private static final String VERSION_HEADER = "X-API-Version";

    @Bean
    public OpenAPI nflOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NFL API")
                        .version("2.0")
                        .contact(new Contact().name("NFL API"))
                        .description("""
                                API RESTful sobre a NFL: **times, jogadores, estádios, patrocinadores e partidas**.

                                ### Autenticação (X-API-Key)
                                1. Gere uma chave pública em `POST /api/keys/register` (nível READ_ONLY), ou use a chave
                                   ADMIN de desenvolvimento `nfl_admin_dev_key_123`.
                                2. Clique em **Authorize** e informe a chave.

                                | Nível | Permissões |
                                |---|---|
                                | READ_ONLY | GET |
                                | READ_WRITE | GET, POST, PUT, PATCH |
                                | ADMIN | tudo, inclusive DELETE e gestão de chaves |

                                ### Idempotência
                                Todo `POST` exige o header `X-Idempotency-Key` (ex.: um UUID). Repetir a mesma chave com o mesmo
                                corpo devolve a resposta original (header `Idempotent-Replayed: true`) sem criar outro registro.

                                ### Rate limiting
                                Limite por IP. Toda resposta traz `X-RateLimit-Limit`, `X-RateLimit-Remaining` e `X-RateLimit-Reset`.
                                Ao exceder: **429 Too Many Requests** com `Retry-After` (segundos).

                                ### Versionamento (X-API-Version)
                                `GET /api/players/{id}` possui duas versões: `1` (padrão) e `2` (enviando `X-API-Version: 2`).

                                ### HATEOAS
                                Respostas em HAL (`_links`, `_embedded`). Comece em `GET /api`.
                                """))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(ApiKeyAuthFilter.HEADER)
                        .description("Chave de API. Dev: nfl_admin_dev_key_123 (ADMIN)")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }

    /**
     * Um grupo por versão da API (seletor "Select a definition" no Swagger UI). Como o OpenAPI não
     * permite duas operações GET no mesmo caminho, cada grupo mostra apenas a sua versão de
     * GET /api/players/{id}; os demais endpoints aparecem em ambos.
     */
    @Bean
    public GroupedOpenApi v1Api() {
        return GroupedOpenApi.builder()
                .group("v1")
                .displayName("NFL API - versão 1 (padrão)")
                .pathsToMatch("/api/**")
                .addOpenApiMethodFilter(method -> !"2".equals(apiVersionOf(method)))
                .build();
    }

    @Bean
    public GroupedOpenApi v2Api() {
        return GroupedOpenApi.builder()
                .group("v2")
                .displayName("NFL API - versão 2 (X-API-Version: 2)")
                .pathsToMatch("/api/**")
                .addOpenApiMethodFilter(method -> !"1".equals(apiVersionOf(method)))
                .build();
    }

    /** Versão declarada no mapeamento (ex.: @GetMapping(version = "2")), ou null se não versionado. */
    private static String apiVersionOf(Method method) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        return mapping != null && !mapping.version().isEmpty() ? mapping.version() : null;
    }

    /**
     * O springdoc documenta o header X-API-Version automaticamente, mas com o valor padrão "1.0.0"
     * (formato semântico interno). Aqui o header passa a mostrar exatamente a versão do grupo.
     */
    @Bean
    public GlobalOpenApiCustomizer versionHeaderCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                if (operation.getParameters() == null) {
                    return;
                }
                operation.getParameters().stream()
                        .filter(p -> VERSION_HEADER.equalsIgnoreCase(p.getName()))
                        .forEach(p -> {
                            List<?> values = p.getSchema() != null ? p.getSchema().getEnum() : null;
                            String version = values != null && values.size() == 1 ? String.valueOf(values.get(0)) : "1";
                            p.setRequired("2".equals(version));
                            p.setDescription("1".equals(version)
                                    ? "Versão da API. Se omitido, usa 1 (padrão)."
                                    : "Envie 2 para obter o formato da versão 2.");
                            p.setSchema(new StringSchema()._default(version).addEnumItem(version));
                        });
            }));
        };
    }

    /**
     * Acrescenta a todas as operações o header de idempotência (POST) e as respostas de erro
     * comuns geradas pelos filtros (400, 401, 403, 429, 500).
     */
    @Bean
    public OperationCustomizer commonHeadersAndResponses() {
        return (operation, handlerMethod) -> {
            boolean isPost = handlerMethod.hasMethodAnnotation(PostMapping.class);
            boolean isPublic = handlerMethod.hasMethodAnnotation(
                    io.swagger.v3.oas.annotations.security.SecurityRequirements.class);


            if (isPost) {
                operation.addParametersItem(new HeaderParameter()
                        .name(IdempotencyFilter.HEADER)
                        .required(true)
                        .description("Chave única da operação (ex.: UUID). Reenvios com a mesma chave não criam duplicatas.")
                        .schema(new StringSchema().example("8f14e45f-ceea-467a-9575-0f2b1a3c6d7e")));
            }

            ApiResponses responses = operation.getResponses();
            addIfAbsent(responses, "400", "Requisição inválida (validação, JSON malformado ou header ausente)");
            if (!isPublic) {
                addIfAbsent(responses, "401", "Chave de API ausente, inválida ou revogada");
                addIfAbsent(responses, "403", "Nível de acesso da chave insuficiente");
            }
            if (isPost) {
                addIfAbsent(responses, "409", "Conflito (dados duplicados ou requisição idempotente em andamento)");
                addIfAbsent(responses, "422", "X-Idempotency-Key reutilizada com corpo diferente / regra de negócio");
            }
            addIfAbsent(responses, "429", "Limite de requisições excedido (veja o header Retry-After)");
            addIfAbsent(responses, "500", "Erro interno inesperado");
            return operation;
        };
    }

    private static void addIfAbsent(ApiResponses responses, String code, String description) {
        if (!responses.containsKey(code)) {
            responses.addApiResponse(code, new ApiResponse()
                    .description(description)
                    .content(new Content().addMediaType("application/json",
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
        }
    }
}
