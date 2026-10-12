package com.nfl.api.dto;

import com.nfl.api.domain.Player;
import com.nfl.api.domain.Team;
import com.nfl.api.domain.enums.Conference;
import com.nfl.api.domain.enums.Position;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import org.springframework.hateoas.server.core.Relation;

import java.time.LocalDate;
import java.time.Period;

public final class PlayerDtos {

    private PlayerDtos() {
    }

    @Schema(name = "PlayerRequest", description = "Dados para criar ou atualizar um jogador")
    public record PlayerRequest(
            @Schema(description = "Primeiro nome", example = "Patrick")
            @NotBlank(message = "O primeiro nome é obrigatório")
            @Size(max = 40)
            String firstName,

            @Schema(description = "Sobrenome", example = "Mahomes")
            @NotBlank(message = "O sobrenome é obrigatório")
            @Size(max = 40)
            String lastName,

            @Schema(description = "Número da camisa (0 a 99)", example = "15")
            @NotNull(message = "O número da camisa é obrigatório")
            @Min(value = 0, message = "O número da camisa deve estar entre 0 e 99")
            @Max(value = 99, message = "O número da camisa deve estar entre 0 e 99")
            Integer jerseyNumber,

            @Schema(description = "Posição", example = "QB")
            @NotNull(message = "A posição é obrigatória")
            Position position,

            @Schema(description = "Data de nascimento (yyyy-MM-dd)", example = "1995-09-17")
            @NotNull(message = "A data de nascimento é obrigatória")
            @Past(message = "A data de nascimento deve estar no passado")
            LocalDate birthDate,

            @Schema(description = "Altura em centímetros", example = "188")
            @Min(value = 150, message = "Altura mínima de 150 cm")
            @Max(value = 230, message = "Altura máxima de 230 cm")
            Integer heightCm,

            @Schema(description = "Peso em quilos", example = "102")
            @Min(value = 60, message = "Peso mínimo de 60 kg")
            @Max(value = 200, message = "Peso máximo de 200 kg")
            Integer weightKg,

            @Schema(description = "Universidade de origem", example = "Texas Tech")
            @Size(max = 80)
            String college,

            @Schema(description = "ID do time (opcional; jogador sem time = free agent)", example = "1")
            @Positive
            Long teamId
    ) {
    }

    /** Versão 1 da representação de jogador (padrão). */
    @Relation(collectionRelation = "players", itemRelation = "player")
    @Schema(name = "PlayerResponse", description = "Representação de um jogador (v1)")
    public record PlayerResponse(
            @Schema(example = "1") Long id,
            @Schema(example = "Patrick") String firstName,
            @Schema(example = "Mahomes") String lastName,
            @Schema(example = "15") Integer jerseyNumber,
            @Schema(example = "QB") Position position,
            @Schema(example = "1995-09-17") LocalDate birthDate,
            @Schema(example = "188") Integer heightCm,
            @Schema(example = "102") Integer weightKg,
            @Schema(example = "Texas Tech") String college,
            @Schema(example = "1") Long teamId,
            @Schema(example = "Kansas City Chiefs") String teamName
    ) {
        public static PlayerResponse from(Player p) {
            Team t = p.getTeam();
            return new PlayerResponse(p.getId(), p.getFirstName(), p.getLastName(), p.getJerseyNumber(),
                    p.getPosition(), p.getBirthDate(), p.getHeightCm(), p.getWeightKg(), p.getCollege(),
                    t != null ? t.getId() : null, t != null ? t.getName() : null);
        }
    }

    /**
     * Versão 2 da representação de jogador (X-API-Version: 2).
     * Mudanças: nome completo, idade calculada, medidas em unidades imperiais
     * e o time como objeto aninhado em vez de campos soltos.
     */
    @Relation(collectionRelation = "players", itemRelation = "player")
    @Schema(name = "PlayerResponseV2", description = "Representação de um jogador (v2)")
    public record PlayerResponseV2(
            @Schema(example = "1") Long id,
            @Schema(example = "Patrick Mahomes") String fullName,
            @Schema(example = "15") Integer jerseyNumber,
            @Schema(example = "QB") Position position,
            @Schema(example = "1995-09-17") LocalDate birthDate,
            @Schema(description = "Idade calculada", example = "31") Integer age,
            @Schema(description = "Medidas físicas") Measurements measurements,
            @Schema(example = "Texas Tech") String college,
            @Schema(description = "Time atual (null para free agent)") TeamSummary team
    ) {
        @Schema(name = "PlayerMeasurementsV2")
        public record Measurements(
                @Schema(example = "188") Integer heightCm,
                @Schema(example = "102") Integer weightKg,
                @Schema(example = "6'2\"") String heightImperial,
                @Schema(example = "225") Integer weightLbs) {
        }

        @Schema(name = "PlayerTeamSummaryV2")
        public record TeamSummary(
                @Schema(example = "1") Long id,
                @Schema(example = "Kansas City Chiefs") String name,
                @Schema(example = "KC") String abbreviation,
                @Schema(example = "AFC") Conference conference) {
        }

        public static PlayerResponseV2 from(Player p) {
            Team t = p.getTeam();
            String heightImperial = null;
            if (p.getHeightCm() != null) {
                long inches = Math.round(p.getHeightCm() / 2.54);
                heightImperial = (inches / 12) + "'" + (inches % 12) + "\"";
            }
            Integer lbs = p.getWeightKg() != null ? (int) Math.round(p.getWeightKg() * 2.20462) : null;
            return new PlayerResponseV2(
                    p.getId(), p.getFullName(), p.getJerseyNumber(), p.getPosition(), p.getBirthDate(),
                    Period.between(p.getBirthDate(), LocalDate.now()).getYears(),
                    new Measurements(p.getHeightCm(), p.getWeightKg(), heightImperial, lbs),
                    p.getCollege(),
                    t != null ? new TeamSummary(t.getId(), t.getName(), t.getAbbreviation(), t.getConference()) : null);
        }
    }
}
