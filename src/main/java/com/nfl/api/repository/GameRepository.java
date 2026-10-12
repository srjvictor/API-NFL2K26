package com.nfl.api.repository;

import com.nfl.api.domain.Game;
import com.nfl.api.domain.enums.GameStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameRepository extends JpaRepository<Game, Long> {

    /** Todos os jogos de um time, seja como mandante ou visitante. */
    @Query("select g from Game g where g.homeTeam.id = :teamId or g.awayTeam.id = :teamId")
    Page<Game> findByTeam(@Param("teamId") Long teamId, Pageable pageable);

    Page<Game> findByStadiumId(Long stadiumId, Pageable pageable);

    @Query("select count(g) > 0 from Game g where g.homeTeam.id = :teamId or g.awayTeam.id = :teamId")
    boolean existsByTeam(@Param("teamId") Long teamId);

    boolean existsByStadiumId(Long stadiumId);

    /** Busca personalizada: temporada, semana e status (semana e status opcionais). */
    @Query("""
            select g from Game g
            where g.season = :season
              and (:week is null or g.week = :week)
              and (:status is null or g.status = :status)
            """)
    Page<Game> search(@Param("season") int season,
                      @Param("week") Integer week,
                      @Param("status") GameStatus status,
                      Pageable pageable);
}
