package com.nfl.api.dto;

import com.nfl.api.domain.Sponsor;
import com.nfl.api.domain.Team;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.util.List;

public final class SponsorDtos {

    private SponsorDtos() {
    }

    @Schema(name = "SponsorRequest", description = "Dados para criar ou atualizar um patrocinador")
    public record SponsorRequest(
            @Schema(description = "Nome da empresa", example = "Nike")
            @NotBlank(message = "O nome do patrocinador é obrigatório")
            @Size(max = 80)
            String name,

            @Schema(description = "Setor de atuação", example = "Material esportivo")
            @NotBlank(message = "O setor de atuação é obrigatório")
            @Size(max = 60)
            String industry,

            @Schema(description = "Valor anual do contrato em dólares", example = "220000000.00")
            @NotNull(message = "O valor anual é obrigatório")
            @Positive(message = "O valor anual deve ser positivo")
            @Digits(integer = 12, fraction = 2, message = "Valor com no máximo 12 dígitos inteiros e 2 decimais")
            BigDecimal annualValueUsd,

            @Schema(description = "Site oficial", example = "https://www.nike.com")
            @Pattern(regexp = "^https?://.+", message = "O site deve começar com http:// ou https://")
            @Size(max = 120)
            String website
    ) {
    }

    @Relation(collectionRelation = "sponsors", itemRelation = "sponsor")
    @Schema(name = "SponsorResponse", description = "Representação de um patrocinador")
    public record SponsorResponse(
            @Schema(example = "1") Long id,
            @Schema(example = "Nike") String name,
            @Schema(example = "Material esportivo") String industry,
            @Schema(example = "220000000.00") BigDecimal annualValueUsd,
            @Schema(example = "https://www.nike.com") String website,
            @Schema(description = "Siglas dos times patrocinados (Many-to-Many)", example = "[\"KC\", \"PHI\"]")
            List<String> teams
    ) {
        public static SponsorResponse from(Sponsor s) {
            return new SponsorResponse(s.getId(), s.getName(), s.getIndustry(), s.getAnnualValueUsd(),
                    s.getWebsite(), s.getTeams().stream().map(Team::getAbbreviation).sorted().toList());
        }
    }
}
