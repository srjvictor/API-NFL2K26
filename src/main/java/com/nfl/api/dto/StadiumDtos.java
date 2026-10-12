package com.nfl.api.dto;

import com.nfl.api.domain.Stadium;
import com.nfl.api.domain.enums.RoofType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import org.springframework.hateoas.server.core.Relation;

public final class StadiumDtos {

    private StadiumDtos() {
    }

    @Schema(name = "StadiumRequest", description = "Dados para criar ou atualizar um estádio")
    public record StadiumRequest(
            @Schema(description = "Nome do estádio", example = "Lincoln Financial Field")
            @NotBlank(message = "O nome do estádio é obrigatório")
            @Size(max = 80)
            String name,

            @Schema(description = "Cidade", example = "Philadelphia")
            @NotBlank(message = "A cidade é obrigatória")
            @Size(max = 60)
            String city,

            @Schema(description = "Capacidade de público", example = "69796")
            @NotNull(message = "A capacidade é obrigatória")
            @Min(value = 1000, message = "A capacidade mínima é 1.000 lugares")
            @Max(value = 200000, message = "A capacidade máxima é 200.000 lugares")
            Integer capacity,

            @Schema(description = "Tipo de cobertura", example = "OPEN")
            @NotNull(message = "O tipo de cobertura é obrigatório")
            RoofType roofType,

            @Schema(description = "Ano de inauguração", example = "2003")
            @Min(value = 1850, message = "Ano de inauguração inválido")
            @Max(value = 2100, message = "Ano de inauguração inválido")
            Integer openedYear
    ) {
    }

    @Relation(collectionRelation = "stadiums", itemRelation = "stadium")
    @Schema(name = "StadiumResponse", description = "Representação de um estádio")
    public record StadiumResponse(
            @Schema(example = "2") Long id,
            @Schema(example = "Lincoln Financial Field") String name,
            @Schema(example = "Philadelphia") String city,
            @Schema(example = "69796") Integer capacity,
            @Schema(example = "OPEN") RoofType roofType,
            @Schema(example = "2003") Integer openedYear,
            @Schema(description = "Time mandante (One-to-One)", example = "2") Long teamId,
            @Schema(example = "Philadelphia Eagles") String teamName
    ) {
        public static StadiumResponse from(Stadium s) {
            return new StadiumResponse(s.getId(), s.getName(), s.getCity(), s.getCapacity(),
                    s.getRoofType(), s.getOpenedYear(),
                    s.getTeam() != null ? s.getTeam().getId() : null,
                    s.getTeam() != null ? s.getTeam().getName() : null);
        }
    }
}
