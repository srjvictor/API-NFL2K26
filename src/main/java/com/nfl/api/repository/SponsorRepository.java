package com.nfl.api.repository;

import com.nfl.api.domain.Sponsor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface SponsorRepository extends JpaRepository<Sponsor, Long> {

    boolean existsByNameIgnoreCase(String name);

    /** Patrocinadores de um time (navegação pelo Many-to-Many). */
    Page<Sponsor> findByTeamsId(Long teamId, Pageable pageable);

    /** Busca personalizada: setor (trecho) e valor anual mínimo. */
    @Query("""
            select s from Sponsor s
            where lower(s.industry) like lower(concat('%', :industry, '%'))
              and s.annualValueUsd >= :minValue
            """)
    Page<Sponsor> search(@Param("industry") String industry,
                         @Param("minValue") BigDecimal minValue,
                         Pageable pageable);
}
