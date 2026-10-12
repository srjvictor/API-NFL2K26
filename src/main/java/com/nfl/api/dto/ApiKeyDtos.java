package com.nfl.api.dto;

import com.nfl.api.domain.ApiKey;
import com.nfl.api.domain.enums.ApiKeyRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.hateoas.server.core.Relation;

import java.time.Instant;

public final class ApiKeyDtos {

    private ApiKeyDtos() {
    }

    @Schema(name = "ApiKeyRegisterRequest", description = "Auto-cadastro público: gera uma chave READ_ONLY")
    public record ApiKeyRegisterRequest(
            @Schema(description = "Nome do cliente/aplicação", example = "App de Fantasy Football")
            @NotBlank(message = "O nome do responsável é obrigatório")
            @Size(max = 80)
            String owner,

            @Schema(description = "E-mail de contato", example = "dev@exemplo.com")
            @Email(message = "E-mail inválido")
            @Size(max = 120)
            String email
    ) {
    }

    @Schema(name = "ApiKeyCreateRequest", description = "Criação de chave por um administrador, com nível de acesso")
    public record ApiKeyCreateRequest(
            @Schema(description = "Nome do cliente/aplicação", example = "Painel interno")
            @NotBlank(message = "O nome do responsável é obrigatório")
            @Size(max = 80)
            String owner,

            @Schema(description = "E-mail de contato", example = "admin@exemplo.com")
            @Email(message = "E-mail inválido")
            @Size(max = 120)
            String email,

            @Schema(description = "Nível de acesso", example = "READ_WRITE")
            @NotNull(message = "O nível de acesso é obrigatório")
            ApiKeyRole role
    ) {
    }

    @Relation(collectionRelation = "apiKeys", itemRelation = "apiKey")
    @Schema(name = "ApiKeyResponse", description = "Metadados de uma chave (o valor completo nunca é exibido)")
    public record ApiKeyResponse(
            @Schema(example = "2") Long id,
            @Schema(example = "App de Fantasy Football") String owner,
            @Schema(example = "dev@exemplo.com") String email,
            @Schema(description = "Início da chave, para identificação", example = "nfl_a1b2c3d4") String keyPrefix,
            @Schema(example = "READ_ONLY") ApiKeyRole role,
            @Schema(example = "true") boolean active,
            Instant createdAt,
            Instant lastUsedAt
    ) {
        public static ApiKeyResponse from(ApiKey k) {
            return new ApiKeyResponse(k.getId(), k.getOwner(), k.getEmail(), k.getKeyPrefix(), k.getRole(),
                    k.isActive(), k.getCreatedAt(), k.getLastUsedAt());
        }
    }

    @Relation(itemRelation = "apiKey")
    @Schema(name = "ApiKeyCreatedResponse", description = "Chave recém-criada. O valor de apiKey só é exibido nesta resposta!")
    public record ApiKeyCreatedResponse(
            @Schema(example = "2") Long id,
            @Schema(example = "App de Fantasy Football") String owner,
            @Schema(example = "READ_ONLY") ApiKeyRole role,
            @Schema(description = "Valor da chave — guarde-o, ele não será exibido novamente",
                    example = "nfl_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v") String apiKey,
            @Schema(example = "Envie esta chave no header X-API-Key") String message
    ) {
    }
}
