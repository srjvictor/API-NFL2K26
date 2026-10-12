package com.nfl.api.domain;

import com.nfl.api.domain.enums.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Registro de uma requisição POST processada com o header X-Idempotency-Key.
 * Guarda a resposta original para que repetições sejam devolvidas sem reprocessamento.
 */
@Entity
@Table(name = "idempotency_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "idempotency_key"}))
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private IdempotencyStatus status;

    private Integer responseStatus;

    @Lob
    private String responseBody;

    private String contentType;

    private String location;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
