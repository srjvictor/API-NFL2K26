package com.nfl.api.web.controller;

import com.nfl.api.domain.enums.GameStatus;
import com.nfl.api.dto.GameDtos.GameRequest;
import com.nfl.api.dto.GameDtos.GameResponse;
import com.nfl.api.dto.GameDtos.GameScoreRequest;
import com.nfl.api.exception.ApiError;
import com.nfl.api.service.GameService;
import com.nfl.api.web.assembler.GameModelAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games")
@Tag(name = "Partidas", description = "Partidas — CRUD, busca por temporada/semana, jogos de um time e atualização de placar")
public class GameController {

    private final GameService service;
    private final GameModelAssembler assembler;
    private final PagedResourcesAssembler<GameResponse> pagedAssembler;

    public GameController(GameService service, GameModelAssembler assembler,
                          PagedResourcesAssembler<GameResponse> pagedAssembler) {
        this.service = service;
        this.assembler = assembler;
        this.pagedAssembler = pagedAssembler;
    }

    @GetMapping
    @Operation(summary = "Listar partidas (paginado)", description = "Retorna uma página de partidas com links HATEOAS.")
    @ApiResponse(responseCode = "200", description = "Página de partidas")
    public ResponseEntity<PagedModel<EntityModel<GameResponse>>> findAll(
            @ParameterObject @PageableDefault(size = 10, sort = "kickoff") Pageable pageable) {
        return ResponseEntity.ok(pagedAssembler.toModel(service.findAll(pageable), assembler));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar partida por ID", description = "Retorna a partida com links para os dois times e o estádio.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Partida encontrada"),
            @ApiResponse(responseCode = "404", description = "Partida não encontrada",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<GameResponse>> findById(
            @Parameter(description = "ID da partida", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(assembler.toModel(service.findById(id)));
    }

    @GetMapping("/search")
    @Operation(summary = "Buscar partidas por temporada, semana e status",
            description = "Consulta personalizada (JPQL). season é obrigatório; week e status são opcionais. "
                    + "Exemplo: /api/games/search?season=2025&week=1")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página com as partidas encontradas"),
            @ApiResponse(responseCode = "400", description = "Parâmetros ausentes ou inválidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<PagedModel<EntityModel<GameResponse>>> search(
            @Parameter(description = "Temporada", example = "2025", required = true)
            @RequestParam @Min(value = 1920, message = "Temporada inválida") Integer season,
            @Parameter(description = "Semana (1-22)", example = "1")
            @RequestParam(required = false) @Min(value = 1, message = "Semana mínima 1") @Max(value = 22, message = "Semana máxima 22") Integer week,
            @Parameter(description = "Status", example = "FINAL") @RequestParam(required = false) GameStatus status,
            @ParameterObject @PageableDefault(size = 10, sort = "kickoff") Pageable pageable) {
        return ResponseEntity.ok(pagedAssembler.toModel(service.search(season, week, status, pageable), assembler));
    }

    @GetMapping("/team/{teamId}")
    @Operation(summary = "Listar partidas de um time (paginado)", description = "Jogos em que o time foi mandante ou visitante.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de partidas do time"),
            @ApiResponse(responseCode = "404", description = "Time não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<PagedModel<EntityModel<GameResponse>>> findByTeam(
            @Parameter(description = "ID do time", example = "1") @PathVariable Long teamId,
            @ParameterObject @PageableDefault(size = 10, sort = "kickoff") Pageable pageable) {
        return ResponseEntity.ok(pagedAssembler.toModel(service.findByTeam(teamId, pageable), assembler));
    }

    @PostMapping
    @Operation(summary = "Agendar/cadastrar partida",
            description = "Cria uma partida. Se stadiumId não for enviado, usa o estádio do mandante. "
                    + "Requer chave READ_WRITE e o header X-Idempotency-Key.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(name = "Bills x Chiefs", value = """
                            {"season":2026,"week":10,"kickoff":"2026-11-15T16:25:00","homeTeamId":5,"awayTeamId":1,
                             "stadiumId":null,"homeScore":null,"awayScore":null,"status":"SCHEDULED"}"""))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Partida criada"),
            @ApiResponse(responseCode = "404", description = "Time ou estádio não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "Regra de negócio violada (ex.: mandante = visitante)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<GameResponse>> create(@Valid @RequestBody GameRequest request) {
        EntityModel<GameResponse> model = assembler.toModel(service.create(request));
        return ResponseEntity.created(model.getRequiredLink("self").toUri()).body(model);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar partida", description = "Substitui os dados da partida. Requer chave READ_WRITE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Partida atualizada"),
            @ApiResponse(responseCode = "404", description = "Partida, time ou estádio não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "Regra de negócio violada",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<GameResponse>> update(
            @Parameter(description = "ID da partida", example = "1") @PathVariable Long id,
            @Valid @RequestBody GameRequest request) {
        return ResponseEntity.ok(assembler.toModel(service.update(id, request)));
    }

    @PatchMapping("/{id}/score")
    @Operation(summary = "Atualizar placar", description = "Atualização parcial de placar e status. Requer chave READ_WRITE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Placar atualizado"),
            @ApiResponse(responseCode = "404", description = "Partida não encontrada",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "Status incompatível com placar (SCHEDULED/CANCELED)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<GameResponse>> updateScore(
            @Parameter(description = "ID da partida", example = "2") @PathVariable Long id,
            @Valid @RequestBody GameScoreRequest request) {
        return ResponseEntity.ok(assembler.toModel(service.updateScore(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir partida", description = "Remove a partida. Requer chave ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Partida excluída"),
            @ApiResponse(responseCode = "404", description = "Partida não encontrada",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> delete(@Parameter(description = "ID da partida", example = "1") @PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
