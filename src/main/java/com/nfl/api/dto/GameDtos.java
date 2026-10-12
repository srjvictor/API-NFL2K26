package com.nfl.api.dto;

import com.nfl.api.domain.Game;
import com.nfl.api.domain.enums.GameStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import org.springframework.hateoas.server.core.Relation;

import java.time.LocalDateTime;

public final class GameDtos {

    private GameDtos() {
    }

    @Schema(name = "GameRequest", description = "Dados para criar ou atualizar uma partida")
    public record GameRequest(
            @Schema(description = "Temporada", example = "2026")
            @NotNull(message = "A temporada é obrigatória")
            @Min(value = 1920, message = "Temporada inválida")
            @Max(value = 2100, message = "Temporada inválida")
            Integer season,

            @Schema(description = "Semana (1-18 temporada regular, 19-22 playoffs)", example = "1")
            @NotNull(message = "A semana é obrigatória")
            @Min(value = 1, message = "A semana deve estar entre 1 e 22")
            @Max(value = 22, message = "A semana deve estar entre 1 e 22")
            Integer week,

            @Schema(description = "Data e hora do kickoff", example = "2026-09-10T20:20:00")
            @NotNull(message = "A data/hora do kickoff é obrigatória")
            LocalDateTime kickoff,

            @Schema(description = "ID do time mandante", example = "2")
            @NotNull(message = "O time mandante é obrigatório")
            @Positive
            Long homeTeamId,

            @Schema(description = "ID do time visitante", example = "4")
            @NotNull(message = "O time visitante é obrigatório")
            @Positive
            Long awayTeamId,

            @Schema(description = "ID do estádio (opcional; padrão = estádio do mandante)", example = "2")
            @Positive
            Long stadiumId,

            @Schema(description = "Placar do mandante", example = "0")
            @PositiveOrZero(message = "O placar não pode ser negativo")
            Integer homeScore,

            @Schema(description = "Placar do visitante", example = "0")
            @PositiveOrZero(message = "O placar não pode ser negativo")
            Integer awayScore,

            @Schema(description = "Status (padrão SCHEDULED)", example = "SCHEDULED")
            GameStatus status
    ) {
    }

    @Schema(name = "GameScoreRequest", description = "Atualização parcial do placar/status de uma partida")
    public record GameScoreRequest(
            @Schema(example = "27")
            @NotNull(message = "O placar do mandante é obrigatório")
            @PositiveOrZero(message = "O placar não pode ser negativo")
            Integer homeScore,

            @Schema(example = "24")
            @NotNull(message = "O placar do visitante é obrigatório")
            @PositiveOrZero(message = "O placar não pode ser negativo")
            Integer awayScore,

            @Schema(example = "FINAL")
            @NotNull(message = "O status é obrigatório")
            GameStatus status
    ) {
    }

    @Relation(collectionRelation = "games", itemRelation = "game")
    @Schema(name = "GameResponse", description = "Representação de uma partida")
    public record GameResponse(
            @Schema(example = "1") Long id,
            @Schema(example = "2025") Integer season,
            @Schema(example = "1") Integer week,
            @Schema(example = "2025-09-04T20:20:00") LocalDateTime kickoff,
            @Schema(example = "FINAL") GameStatus status,
            @Schema(example = "2") Long homeTeamId,
            @Schema(example = "Philadelphia Eagles") String homeTeamName,
            @Schema(example = "4") Long awayTeamId,
            @Schema(example = "Dallas Cowboys") String awayTeamName,
            @Schema(example = "2") Long stadiumId,
            @Schema(example = "Lincoln Financial Field") String stadiumName,
            @Schema(example = "24") Integer homeScore,
            @Schema(example = "20") Integer awayScore,
            @Schema(description = "Vencedor (apenas para jogos FINAL)", example = "Philadelphia Eagles") String winner
    ) {
        public static GameResponse from(Game g) {
            String winner = null;
            if (g.getStatus() == GameStatus.FINAL && g.getHomeScore() != null && g.getAwayScore() != null) {
                int cmp = Integer.compare(g.getHomeScore(), g.getAwayScore());
                winner = cmp > 0 ? g.getHomeTeam().getName() : cmp < 0 ? g.getAwayTeam().getName() : "TIE";
            }
            return new GameResponse(g.getId(), g.getSeason(), g.getWeek(), g.getKickoff(), g.getStatus(),
                    g.getHomeTeam().getId(), g.getHomeTeam().getName(),
                    g.getAwayTeam().getId(), g.getAwayTeam().getName(),
                    g.getStadium() != null ? g.getStadium().getId() : null,
                    g.getStadium() != null ? g.getStadium().getName() : null,
                    g.getHomeScore(), g.getAwayScore(), winner);
        }
    }
}
