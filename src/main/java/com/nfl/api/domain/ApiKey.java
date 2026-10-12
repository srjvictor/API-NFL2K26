package com.nfl.api.domain;

import com.nfl.api.domain.enums.ApiKeyRole;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Chave de API de um cliente. Apenas o hash SHA-256 da chave é armazenado;
 * o valor em texto puro é exibido uma única vez, na criação.
 */
@Entity
@Table(name = "api_keys")
@Getter
@Setter
@NoArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true, length = 64)
    private String keyHash;

    @NotBlank
    @Column(nullable = false, length = 12)
    private String keyPrefix;

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String owner;

    @Email
    @Size(max = 120)
    private String email;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ApiKeyRole role;

    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant lastUsedAt;
}
