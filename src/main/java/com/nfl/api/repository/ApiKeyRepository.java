package com.nfl.api.repository;

import com.nfl.api.domain.ApiKey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    boolean existsByKeyHash(String keyHash);

    Page<ApiKey> findByOwnerContainingIgnoreCase(String owner, Pageable pageable);
}
