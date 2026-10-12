package com.nfl.api.repository;

import com.nfl.api.domain.Team;
import com.nfl.api.domain.enums.Conference;
import com.nfl.api.domain.enums.Division;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByAbbreviation(String abbreviation);

    boolean existsByStadiumId(Long stadiumId);

    /** Busca personalizada: filtros opcionais por conferência, divisão e trecho do nome. */
    @Query("""
            select t from Team t
            where (:conference is null or t.conference = :conference)
              and (:division is null or t.division = :division)
              and lower(t.name) like lower(concat('%', :name, '%'))
            """)
    Page<Team> search(@Param("conference") Conference conference,
                      @Param("division") Division division,
                      @Param("name") String name,
                      Pageable pageable);
}
