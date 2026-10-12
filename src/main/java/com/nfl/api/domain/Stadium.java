package com.nfl.api.domain;

import com.nfl.api.domain.enums.RoofType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Estádio. Lado inverso do relacionamento One-to-One com {@link Team}. */
@Entity
@Table(name = "stadiums")
@Getter
@Setter
@NoArgsConstructor
public class Stadium {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O nome do estádio é obrigatório")
    @Size(max = 80)
    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @NotBlank(message = "A cidade é obrigatória")
    @Size(max = 60)
    @Column(nullable = false, length = 60)
    private String city;

    @NotNull(message = "A capacidade é obrigatória")
    @Min(value = 1000, message = "A capacidade mínima é 1.000 lugares")
    @Max(value = 200000, message = "A capacidade máxima é 200.000 lugares")
    private Integer capacity;

    @NotNull(message = "O tipo de cobertura é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RoofType roofType;

    @Min(1850)
    @Max(2100)
    private Integer openedYear;

    @OneToOne(mappedBy = "stadium", fetch = FetchType.LAZY)
    private Team team;
}
