package com.nfl.api.domain;

import com.nfl.api.domain.enums.Conference;
import com.nfl.api.domain.enums.Division;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Franquia da NFL.
 * <ul>
 *   <li>One-to-One com {@link Stadium} (estádio sede)</li>
 *   <li>One-to-Many com {@link Player} (elenco)</li>
 *   <li>Many-to-Many com {@link Sponsor} (patrocinadores)</li>
 * </ul>
 */
@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O nome do time é obrigatório")
    @Size(max = 60, message = "O nome do time deve ter no máximo 60 caracteres")
    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @NotBlank(message = "A cidade é obrigatória")
    @Size(max = 60)
    @Column(nullable = false, length = 60)
    private String city;

    @NotBlank(message = "A sigla é obrigatória")
    @Pattern(regexp = "^[A-Z]{2,3}$", message = "A sigla deve ter 2 ou 3 letras maiúsculas")
    @Column(nullable = false, unique = true, length = 3)
    private String abbreviation;

    @NotNull(message = "A conferência é obrigatória")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Conference conference;

    @NotNull(message = "A divisão é obrigatória")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Division division;

    @NotNull
    @Min(value = 1920, message = "A NFL foi fundada em 1920")
    @Max(2100)
    private Integer foundedYear;

    @NotNull
    @PositiveOrZero
    private Integer superBowlTitles = 0;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stadium_id", unique = true)
    private Stadium stadium;

    @OneToMany(mappedBy = "team")
    private List<Player> players = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "team_sponsors",
            joinColumns = @JoinColumn(name = "team_id"),
            inverseJoinColumns = @JoinColumn(name = "sponsor_id"))
    private Set<Sponsor> sponsors = new HashSet<>();
}
