package com.nfl.api.repository;

import com.nfl.api.domain.Player;
import com.nfl.api.domain.enums.Position;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    Page<Player> findByTeamId(Long teamId, Pageable pageable);

    boolean existsByTeamIdAndJerseyNumber(Long teamId, Integer jerseyNumber);

    boolean existsByTeamIdAndJerseyNumberAndIdNot(Long teamId, Integer jerseyNumber, Long id);

    /** Busca personalizada: posição, time e trecho do nome (todos opcionais). */
    @Query("""
            select p from Player p
            where (:position is null or p.position = :position)
              and (:teamId is null or p.team.id = :teamId)
              and lower(concat(p.firstName, ' ', p.lastName)) like lower(concat('%', :name, '%'))
            """)
    Page<Player> search(@Param("position") Position position,
                        @Param("teamId") Long teamId,
                        @Param("name") String name,
                        Pageable pageable);
}
