package com.nfl.api.domain;

import com.nfl.api.domain.enums.Position;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Jogador. Lado "muitos" do relacionamento One-to-Many com {@link Team}. */
@Entity
@Table(name = "players")
@Getter
@Setter
@NoArgsConstructor
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O primeiro nome é obrigatório")
    @Size(max = 40)
    @Column(nullable = false, length = 40)
    private String firstName;

    @NotBlank(message = "O sobrenome é obrigatório")
    @Size(max = 40)
    @Column(nullable = false, length = 40)
    private String lastName;

    @NotNull(message = "O número da camisa é obrigatório")
    @Min(value = 0, message = "O número da camisa deve estar entre 0 e 99")
    @Max(value = 99, message = "O número da camisa deve estar entre 0 e 99")
    private Integer jerseyNumber;

    @NotNull(message = "A posição é obrigatória")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Position position;

    @NotNull(message = "A data de nascimento é obrigatória")
    @Past(message = "A data de nascimento deve estar no passado")
    private LocalDate birthDate;

    @Min(150)
    @Max(230)
    private Integer heightCm;

    @Min(60)
    @Max(200)
    private Integer weightKg;

    @Size(max = 80)
    private String college;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
