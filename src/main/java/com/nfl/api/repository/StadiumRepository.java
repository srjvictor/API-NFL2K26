package com.nfl.api.repository;

import com.nfl.api.domain.Stadium;
import com.nfl.api.domain.enums.RoofType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StadiumRepository extends JpaRepository<Stadium, Long> {

    boolean existsByNameIgnoreCase(String name);

    /** Busca personalizada: capacidade mínima e tipo de cobertura (opcional). */
    @Query("""
            select s from Stadium s
            where s.capacity >= :minCapacity
              and (:roofType is null or s.roofType = :roofType)
            """)
    Page<Stadium> search(@Param("minCapacity") int minCapacity,
                         @Param("roofType") RoofType roofType,
                         Pageable pageable);
}
