package com.nfl.api.domain;

import com.nfl.api.domain.enums.GameStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Partida entre dois times (Many-to-One com {@link Team} e {@link Stadium}). */
@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "A temporada é obrigatória")
    @Min(1920)
    @Max(2100)
    private Integer season;

    @NotNull(message = "A semana é obrigatória")
    @Min(value = 1, message = "A semana deve estar entre 1 e 22")
    @Max(value = 22, message = "A semana deve estar entre 1 e 22")
    private Integer week;

    @NotNull(message = "A data/hora do kickoff é obrigatória")
    private LocalDateTime kickoff;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stadium_id")
    private Stadium stadium;

    @PositiveOrZero
    private Integer homeScore;

    @PositiveOrZero
    private Integer awayScore;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private GameStatus status = GameStatus.SCHEDULED;
}
