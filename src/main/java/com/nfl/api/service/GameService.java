package com.nfl.api.service;

import com.nfl.api.domain.Game;
import com.nfl.api.domain.Stadium;
import com.nfl.api.domain.Team;
import com.nfl.api.domain.enums.GameStatus;
import com.nfl.api.dto.GameDtos.GameRequest;
import com.nfl.api.dto.GameDtos.GameResponse;
import com.nfl.api.dto.GameDtos.GameScoreRequest;
import com.nfl.api.exception.BusinessRuleException;
import com.nfl.api.exception.ResourceNotFoundException;
import com.nfl.api.repository.GameRepository;
import com.nfl.api.repository.StadiumRepository;
import com.nfl.api.repository.TeamRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GameService {

    private final GameRepository gameRepository;
    private final TeamRepository teamRepository;
    private final StadiumRepository stadiumRepository;

    public GameService(GameRepository gameRepository, TeamRepository teamRepository,
                       StadiumRepository stadiumRepository) {
        this.gameRepository = gameRepository;
        this.teamRepository = teamRepository;
        this.stadiumRepository = stadiumRepository;
    }

    @Transactional(readOnly = true)
    public Page<GameResponse> findAll(Pageable pageable) {
        return gameRepository.findAll(pageable).map(GameResponse::from);
    }

    @Transactional(readOnly = true)
    public GameResponse findById(Long id) {
        return GameResponse.from(getGame(id));
    }

    @Transactional(readOnly = true)
    public Page<GameResponse> search(int season, Integer week, GameStatus status, Pageable pageable) {
        return gameRepository.search(season, week, status, pageable).map(GameResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<GameResponse> findByTeam(Long teamId, Pageable pageable) {
        if (!teamRepository.existsById(teamId)) {
            throw new ResourceNotFoundException("Time", teamId);
        }
        return gameRepository.findByTeam(teamId, pageable).map(GameResponse::from);
    }

    public GameResponse create(GameRequest request) {
        Game game = new Game();
        apply(game, request);
        return GameResponse.from(gameRepository.save(game));
    }

    public GameResponse update(Long id, GameRequest request) {
        Game game = getGame(id);
        apply(game, request);
        return GameResponse.from(game);
    }

    public GameResponse updateScore(Long id, GameScoreRequest request) {
        Game game = getGame(id);
        if (request.status() == GameStatus.SCHEDULED || request.status() == GameStatus.CANCELED) {
            throw new BusinessRuleException("Não é possível registrar placar com status " + request.status());
        }
        game.setHomeScore(request.homeScore());
        game.setAwayScore(request.awayScore());
        game.setStatus(request.status());
        return GameResponse.from(game);
    }

    public void delete(Long id) {
        gameRepository.delete(getGame(id));
    }

    private Game getGame(Long id) {
        return gameRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Partida", id));
    }

    private void apply(Game g, GameRequest r) {
        if (r.homeTeamId().equals(r.awayTeamId())) {
            throw new BusinessRuleException("O time mandante e o visitante devem ser diferentes");
        }
        GameStatus status = r.status() != null ? r.status() : GameStatus.SCHEDULED;
        if (status == GameStatus.FINAL && (r.homeScore() == null || r.awayScore() == null)) {
            throw new BusinessRuleException("Partidas com status FINAL precisam do placar dos dois times");
        }
        Team home = teamRepository.findById(r.homeTeamId())
                .orElseThrow(() -> new ResourceNotFoundException("Time mandante", r.homeTeamId()));
        Team away = teamRepository.findById(r.awayTeamId())
                .orElseThrow(() -> new ResourceNotFoundException("Time visitante", r.awayTeamId()));
        Stadium stadium = r.stadiumId() != null
                ? stadiumRepository.findById(r.stadiumId())
                    .orElseThrow(() -> new ResourceNotFoundException("Estádio", r.stadiumId()))
                : home.getStadium();

        g.setSeason(r.season());
        g.setWeek(r.week());
        g.setKickoff(r.kickoff());
        g.setHomeTeam(home);
        g.setAwayTeam(away);
        g.setStadium(stadium);
        g.setHomeScore(r.homeScore());
        g.setAwayScore(r.awayScore());
        g.setStatus(status);
    }
}
