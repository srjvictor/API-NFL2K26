package com.nfl.api.dto;

import com.nfl.api.domain.Sponsor;
import com.nfl.api.domain.Team;
import com.nfl.api.domain.enums.Conference;
import com.nfl.api.domain.enums.Division;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import org.springframework.hateoas.server.core.Relation;

import java.util.List;

public final class TeamDtos {

    private TeamDtos() {
    }

    @Schema(name = "TeamRequest", description = "Dados para criar ou atualizar um time")
    public record TeamRequest(
            @Schema(description = "Nome da franquia", example = "Kansas City Chiefs")
            @NotBlank(message = "O nome do time é obrigatório")
            @Size(max = 60, message = "O nome do time deve ter no máximo 60 caracteres")
            String name,

            @Schema(description = "Cidade sede", example = "Kansas City")
            @NotBlank(message = "A cidade é obrigatória")
            @Size(max = 60)
            String city,

            @Schema(description = "Sigla oficial (2 ou 3 letras maiúsculas)", example = "KC")
            @NotBlank(message = "A sigla é obrigatória")
            @Pattern(regexp = "^[A-Z]{2,3}$", message = "A sigla deve ter 2 ou 3 letras maiúsculas")
            String abbreviation,

            @Schema(description = "Conferência", example = "AFC")
            @NotNull(message = "A conferência é obrigatória")
            Conference conference,

            @Schema(description = "Divisão dentro da conferência", example = "WEST")
            @NotNull(message = "A divisão é obrigatória")
            Division division,

            @Schema(description = "Ano de fundação", example = "1960")
            @NotNull(message = "O ano de fundação é obrigatório")
            @Min(value = 1920, message = "A NFL foi fundada em 1920")
            @Max(value = 2100, message = "Ano de fundação inválido")
            Integer foundedYear,

            @Schema(description = "Quantidade de títulos de Super Bowl", example = "4")
            @NotNull(message = "A quantidade de títulos é obrigatória")
            @PositiveOrZero(message = "A quantidade de títulos não pode ser negativa")
            Integer superBowlTitles,

            @Schema(description = "ID do estádio sede (opcional, relação One-to-One)", example = "1")
            @Positive
            Long stadiumId
    ) {
    }

    @Relation(collectionRelation = "teams", itemRelation = "team")
    @Schema(name = "TeamResponse", description = "Representação de um time")
    public record TeamResponse(
            @Schema(example = "1") Long id,
            @Schema(example = "Kansas City Chiefs") String name,
            @Schema(example = "Kansas City") String city,
            @Schema(example = "KC") String abbreviation,
            @Schema(example = "AFC") Conference conference,
            @Schema(example = "WEST") Division division,
            @Schema(example = "1960") Integer foundedYear,
            @Schema(example = "4") Integer superBowlTitles,
            @Schema(example = "1") Long stadiumId,
            @Schema(example = "GEHA Field at Arrowhead Stadium") String stadiumName,
            @Schema(description = "Quantidade de jogadores no elenco", example = "2") int playerCount,
            @Schema(description = "Nomes dos patrocinadores", example = "[\"Nike\", \"Gatorade\"]") List<String> sponsors
    ) {
        public static TeamResponse from(Team t) {
            return new TeamResponse(
                    t.getId(), t.getName(), t.getCity(), t.getAbbreviation(),
                    t.getConference(), t.getDivision(), t.getFoundedYear(), t.getSuperBowlTitles(),
                    t.getStadium() != null ? t.getStadium().getId() : null,
                    t.getStadium() != null ? t.getStadium().getName() : null,
                    t.getPlayers().size(),
                    t.getSponsors().stream().map(Sponsor::getName).sorted().toList());
        }
    }
}
