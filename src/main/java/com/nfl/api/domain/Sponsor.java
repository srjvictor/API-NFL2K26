package com.nfl.api.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/** Patrocinador. Lado inverso do relacionamento Many-to-Many com {@link Team}. */
@Entity
@Table(name = "sponsors")
@Getter
@Setter
@NoArgsConstructor
public class Sponsor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O nome do patrocinador é obrigatório")
    @Size(max = 80)
    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @NotBlank(message = "O setor de atuação é obrigatório")
    @Size(max = 60)
    @Column(nullable = false, length = 60)
    private String industry;

    @NotNull(message = "O valor anual é obrigatório")
    @Positive(message = "O valor anual deve ser positivo")
    @Digits(integer = 12, fraction = 2)
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal annualValueUsd;

    @Pattern(regexp = "^https?://.+", message = "O site deve começar com http:// ou https://")
    @Size(max = 120)
    private String website;

    @ManyToMany(mappedBy = "sponsors")
    private Set<Team> teams = new HashSet<>();
}
