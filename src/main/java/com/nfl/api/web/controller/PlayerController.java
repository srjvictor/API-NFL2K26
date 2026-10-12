package com.nfl.api.web.controller;

import com.nfl.api.domain.enums.Position;
import com.nfl.api.dto.PlayerDtos.PlayerRequest;
import com.nfl.api.dto.PlayerDtos.PlayerResponse;
import com.nfl.api.dto.PlayerDtos.PlayerResponseV2;
import com.nfl.api.exception.ApiError;
import com.nfl.api.service.PlayerService;
import com.nfl.api.web.assembler.PlayerModelAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/players")
@Tag(name = "Jogadores", description = "Jogadores — CRUD, busca e endpoint versionado via X-API-Version")
public class PlayerController {

    private final PlayerService service;
    private final PlayerModelAssembler assembler;
    private final PagedResourcesAssembler<PlayerResponse> pagedAssembler;

    public PlayerController(PlayerService service, PlayerModelAssembler assembler,
                            PagedResourcesAssembler<PlayerResponse> pagedAssembler) {
        this.service = service;
        this.assembler = assembler;
        this.pagedAssembler = pagedAssembler;
    }

    @GetMapping
    @Operation(summary = "Listar jogadores (paginado)", description = "Retorna uma página de jogadores com links HATEOAS.")
    @ApiResponse(responseCode = "200", description = "Página de jogadores")
    public ResponseEntity<PagedModel<EntityModel<PlayerResponse>>> findAll(
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(pagedAssembler.toModel(service.findAll(pageable), assembler));
    }

    /** Versão 1 (padrão quando X-API-Version não é enviado). */
    @GetMapping(path = "/{id}", version = "1")
    @Operation(summary = "Buscar jogador por ID — versão 1",
            description = "Versão 1 (padrão). Campos planos: firstName, lastName, teamId, teamName. "
                    + "Esta versão está marcada como depreciada (header Deprecation: true) — prefira a v2.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jogador encontrado (formato v1)"),
            @ApiResponse(responseCode = "404", description = "Jogador não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<PlayerResponse>> findById(
            @Parameter(description = "ID do jogador", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok()
                .header("X-API-Version", "1")
                .header("Deprecation", "true")
                .body(assembler.toModel(service.findById(id)));
    }

    /** Versão 2: selecionada com o header X-API-Version: 2. */
    @GetMapping(path = "/{id}", version = "2")
    @Operation(summary = "Buscar jogador por ID — versão 2",
            description = "Envie o header X-API-Version: 2. Retorna fullName, idade calculada, medidas em unidades "
                    + "métricas e imperiais e o time como objeto aninhado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jogador encontrado (formato v2)"),
            @ApiResponse(responseCode = "404", description = "Jogador não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<PlayerResponseV2>> findByIdV2(
            @Parameter(description = "ID do jogador", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok()
                .header("X-API-Version", "2")
                .body(assembler.toModelV2(service.findByIdV2(id)));
    }

    @GetMapping("/search")
    @Operation(summary = "Buscar jogadores por posição, time e nome",
            description = "Consulta personalizada (JPQL). Filtros opcionais e combináveis. "
                    + "Exemplo: /api/players/search?position=QB&name=allen")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página com os jogadores encontrados"),
            @ApiResponse(responseCode = "400", description = "Posição inválida",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<PagedModel<EntityModel<PlayerResponse>>> search(
            @Parameter(description = "Posição", example = "QB") @RequestParam(required = false) Position position,
            @Parameter(description = "ID do time", example = "1") @RequestParam(required = false) Long teamId,
            @Parameter(description = "Trecho do nome", example = "mahomes") @RequestParam(required = false) String name,
            @ParameterObject @PageableDefault(size = 10, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(pagedAssembler.toModel(service.search(position, teamId, name, pageable), assembler));
    }

    @PostMapping
    @Operation(summary = "Cadastrar jogador",
            description = "Cria um jogador (teamId opcional). Requer chave READ_WRITE e o header X-Idempotency-Key.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(name = "Lamar Jackson", value = """
                            {"firstName":"Lamar","lastName":"Jackson","jerseyNumber":8,"position":"QB",
                             "birthDate":"1997-01-07","heightCm":188,"weightKg":93,"college":"Louisville","teamId":null}"""))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Jogador criado"),
            @ApiResponse(responseCode = "404", description = "Time informado não existe",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Número de camisa já usado no time",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<PlayerResponse>> create(@Valid @RequestBody PlayerRequest request) {
        EntityModel<PlayerResponse> model = assembler.toModel(service.create(request));
        return ResponseEntity.created(model.getRequiredLink("self").toUri()).body(model);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar jogador", description = "Substitui os dados do jogador (inclusive troca de time). Requer chave READ_WRITE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jogador atualizado"),
            @ApiResponse(responseCode = "404", description = "Jogador ou time não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Número de camisa já usado no time",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<PlayerResponse>> update(
            @Parameter(description = "ID do jogador", example = "1") @PathVariable Long id,
            @Valid @RequestBody PlayerRequest request) {
        return ResponseEntity.ok(assembler.toModel(service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir jogador", description = "Remove o jogador. Requer chave ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Jogador excluído"),
            @ApiResponse(responseCode = "404", description = "Jogador não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> delete(@Parameter(description = "ID do jogador", example = "1") @PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
