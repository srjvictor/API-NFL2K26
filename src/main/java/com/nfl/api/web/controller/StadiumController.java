package com.nfl.api.web.controller;

import com.nfl.api.domain.enums.RoofType;
import com.nfl.api.dto.GameDtos.GameResponse;
import com.nfl.api.dto.StadiumDtos.StadiumRequest;
import com.nfl.api.dto.StadiumDtos.StadiumResponse;
import com.nfl.api.exception.ApiError;
import com.nfl.api.service.StadiumService;
import com.nfl.api.web.assembler.GameModelAssembler;
import com.nfl.api.web.assembler.StadiumModelAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/stadiums")
@Tag(name = "Estádios", description = "Estádios — CRUD, busca por capacidade e jogos sediados")
public class StadiumController {

    private final StadiumService service;
    private final StadiumModelAssembler assembler;
    private final GameModelAssembler gameAssembler;
    private final PagedResourcesAssembler<StadiumResponse> pagedAssembler;
    private final PagedResourcesAssembler<GameResponse> gamePagedAssembler;

    public StadiumController(StadiumService service, StadiumModelAssembler assembler,
                             GameModelAssembler gameAssembler,
                             PagedResourcesAssembler<StadiumResponse> pagedAssembler,
                             PagedResourcesAssembler<GameResponse> gamePagedAssembler) {
        this.service = service;
        this.assembler = assembler;
        this.gameAssembler = gameAssembler;
        this.pagedAssembler = pagedAssembler;
        this.gamePagedAssembler = gamePagedAssembler;
    }

    @GetMapping
    @Operation(summary = "Listar estádios (paginado)", description = "Retorna uma página de estádios com links HATEOAS.")
    @ApiResponse(responseCode = "200", description = "Página de estádios")
    public ResponseEntity<PagedModel<EntityModel<StadiumResponse>>> findAll(
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(pagedAssembler.toModel(service.findAll(pageable), assembler));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar estádio por ID", description = "Retorna o estádio e, se houver, o link para o time mandante (One-to-One).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estádio encontrado"),
            @ApiResponse(responseCode = "404", description = "Estádio não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<StadiumResponse>> findById(
            @Parameter(description = "ID do estádio", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(assembler.toModel(service.findById(id)));
    }

    @GetMapping("/search")
    @Operation(summary = "Buscar estádios por capacidade mínima e cobertura",
            description = "Consulta personalizada (JPQL). Exemplo: /api/stadiums/search?minCapacity=70000&roofType=OPEN")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página com os estádios encontrados"),
            @ApiResponse(responseCode = "400", description = "Parâmetros inválidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<PagedModel<EntityModel<StadiumResponse>>> search(
            @Parameter(description = "Capacidade mínima", example = "70000")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "A capacidade mínima não pode ser negativa") Integer minCapacity,
            @Parameter(description = "Tipo de cobertura", example = "OPEN") @RequestParam(required = false) RoofType roofType,
            @ParameterObject @PageableDefault(size = 10, sort = "capacity", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(pagedAssembler.toModel(service.search(minCapacity, roofType, pageable), assembler));
    }

    @GetMapping("/{id}/games")
    @Operation(summary = "Listar jogos sediados no estádio (paginado)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de partidas"),
            @ApiResponse(responseCode = "404", description = "Estádio não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<PagedModel<EntityModel<GameResponse>>> findGames(
            @Parameter(description = "ID do estádio", example = "2") @PathVariable Long id,
            @ParameterObject @PageableDefault(size = 10, sort = "kickoff") Pageable pageable) {
        return ResponseEntity.ok(gamePagedAssembler.toModel(service.findGames(id, pageable), gameAssembler));
    }

    @PostMapping
    @Operation(summary = "Cadastrar estádio",
            description = "Cria um estádio. Requer chave READ_WRITE e o header X-Idempotency-Key.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(name = "Ford Field", value = """
                            {"name":"Ford Field","city":"Detroit","capacity":65000,"roofType":"DOME","openedYear":2002}"""))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Estádio criado"),
            @ApiResponse(responseCode = "409", description = "Nome duplicado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<StadiumResponse>> create(@Valid @RequestBody StadiumRequest request) {
        EntityModel<StadiumResponse> model = assembler.toModel(service.create(request));
        return ResponseEntity.created(model.getRequiredLink("self").toUri()).body(model);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar estádio", description = "Substitui os dados do estádio. Requer chave READ_WRITE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estádio atualizado"),
            @ApiResponse(responseCode = "404", description = "Estádio não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Nome duplicado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<StadiumResponse>> update(
            @Parameter(description = "ID do estádio", example = "1") @PathVariable Long id,
            @Valid @RequestBody StadiumRequest request) {
        return ResponseEntity.ok(assembler.toModel(service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir estádio", description = "Só é possível excluir estádios sem time e sem partidas. Requer chave ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Estádio excluído"),
            @ApiResponse(responseCode = "404", description = "Estádio não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Estádio em uso por um time ou partida",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> delete(@Parameter(description = "ID do estádio", example = "6") @PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
