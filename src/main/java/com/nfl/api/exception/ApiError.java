package com.nfl.api.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Corpo padrão de todas as respostas de erro da API. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(name = "ApiError", description = "Formato padrão de erro")
public record ApiError(
        @Schema(example = "2026-10-12T18:30:00Z") Instant timestamp,
        @Schema(example = "404") int status,
        @Schema(example = "Not Found") String error,
        @Schema(example = "Time com id 99 não encontrado") String message,
        @Schema(example = "/api/teams/99") String path,
        @Schema(description = "Erros de validação por campo (quando houver)") List<FieldErrorItem> fieldErrors
) {

    @Schema(name = "FieldError")
    public record FieldErrorItem(
            @Schema(example = "abbreviation") String field,
            @Schema(example = "A sigla deve ter 2 ou 3 letras maiúsculas") String message,
            @Schema(example = "kc") Object rejectedValue) {
    }

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }
}
