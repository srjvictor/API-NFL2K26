package com.nfl.api.web.controller;

import com.nfl.api.domain.enums.Conference;
import com.nfl.api.domain.enums.Division;
import com.nfl.api.dto.PlayerDtos.PlayerResponse;
import com.nfl.api.dto.SponsorDtos.SponsorResponse;
import com.nfl.api.dto.TeamDtos.TeamRequest;
import com.nfl.api.dto.TeamDtos.TeamResponse;
import com.nfl.api.exception.ApiError;
import com.nfl.api.service.TeamService;
import com.nfl.api.web.assembler.PlayerModelAssembler;
import com.nfl.api.web.assembler.SponsorModelAssembler;
import com.nfl.api.web.assembler.TeamModelAssembler;
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
@RequestMapping("/api/teams")
@Tag(name = "Times", description = "Franquias da NFL — CRUD, busca, elenco e patrocinadores (Many-to-Many)")
public class TeamController {

    private final TeamService service;
    private final TeamModelAssembler assembler;
    private final PlayerModelAssembler playerAssembler;
    private final SponsorModelAssembler sponsorAssembler;
    private final PagedResourcesAssembler<TeamResponse> pagedAssembler;
    private final PagedResourcesAssembler<PlayerResponse> playerPagedAssembler;
    private final PagedResourcesAssembler<SponsorResponse> sponsorPagedAssembler;

    public TeamController(TeamService service, TeamModelAssembler assembler,
                          PlayerModelAssembler playerAssembler, SponsorModelAssembler sponsorAssembler,
                          PagedResourcesAssembler<TeamResponse> pagedAssembler,
                          PagedResourcesAssembler<PlayerResponse> playerPagedAssembler,
                          PagedResourcesAssembler<SponsorResponse> sponsorPagedAssembler) {
        this.service = service;
        this.assembler = assembler;
        this.playerAssembler = playerAssembler;
        this.sponsorAssembler = sponsorAssembler;
        this.pagedAssembler = pagedAssembler;
        this.playerPagedAssembler = playerPagedAssembler;
        this.sponsorPagedAssembler = sponsorPagedAssembler;
    }

    @GetMapping
    @Operation(summary = "Listar times (paginado)",
            description = "Retorna uma página de times com links HATEOAS de navegação (first, prev, next, last). "
                    + "Use page, size e sort (ex.: sort=name,asc).")
    @ApiResponse(responseCode = "200", description = "Página de times")
    public ResponseEntity<PagedModel<EntityModel<TeamResponse>>> findAll(
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(pagedAssembler.toModel(service.findAll(pageable), assembler));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar time por ID", description = "Retorna o time com links para elenco, estádio, jogos e patrocinadores.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Time encontrado"),
            @ApiResponse(responseCode = "404", description = "Time não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<TeamResponse>> findById(
            @Parameter(description = "ID do time", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(assembler.toModel(service.findById(id)));
    }

    @GetMapping("/search")
    @Operation(summary = "Buscar times por conferência, divisão e nome",
            description = "Consulta personalizada (JPQL). Todos os filtros são opcionais e combináveis. "
                    + "Exemplo: /api/teams/search?conference=NFC&division=EAST")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página com os times encontrados"),
            @ApiResponse(responseCode = "400", description = "Valor de enum inválido",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<PagedModel<EntityModel<TeamResponse>>> search(
            @Parameter(description = "Conferência", example = "NFC") @RequestParam(required = false) Conference conference,
            @Parameter(description = "Divisão", example = "EAST") @RequestParam(required = false) Division division,
            @Parameter(description = "Trecho do nome", example = "eagles") @RequestParam(required = false) String name,
            @ParameterObject @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(pagedAssembler.toModel(service.search(conference, division, name, pageable), assembler));
    }

    @PostMapping
    @Operation(summary = "Cadastrar time",
            description = "Cria um novo time. Requer chave READ_WRITE ou ADMIN e o header X-Idempotency-Key.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(name = "Detroit Lions", value = """
                            {"name":"Detroit Lions","city":"Detroit","abbreviation":"DET","conference":"NFC",
                             "division":"NORTH","foundedYear":1930,"superBowlTitles":0,"stadiumId":null}"""))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Time criado; header Location aponta para o recurso"),
            @ApiResponse(responseCode = "404", description = "Estádio informado não existe",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Nome/sigla duplicados ou estádio já ocupado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<TeamResponse>> create(@Valid @RequestBody TeamRequest request) {
        EntityModel<TeamResponse> model = assembler.toModel(service.create(request));
        return ResponseEntity.created(model.getRequiredLink("self").toUri()).body(model);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar time", description = "Substitui todos os dados do time. Requer chave READ_WRITE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Time atualizado"),
            @ApiResponse(responseCode = "404", description = "Time ou estádio não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Nome/sigla duplicados ou estádio já ocupado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<TeamResponse>> update(
            @Parameter(description = "ID do time", example = "1") @PathVariable Long id,
            @Valid @RequestBody TeamRequest request) {
        return ResponseEntity.ok(assembler.toModel(service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir time",
            description = "Remove o time. Os jogadores viram free agents e os vínculos com patrocinadores são desfeitos. "
                    + "Requer chave ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Time excluído"),
            @ApiResponse(responseCode = "404", description = "Time não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Time possui partidas cadastradas",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> delete(@Parameter(description = "ID do time", example = "1") @PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/players")
    @Operation(summary = "Listar elenco do time (paginado)", description = "Navegação pelo relacionamento One-to-Many Time → Jogadores.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de jogadores do time"),
            @ApiResponse(responseCode = "404", description = "Time não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<PagedModel<EntityModel<PlayerResponse>>> findPlayers(
            @Parameter(description = "ID do time", example = "1") @PathVariable Long id,
            @ParameterObject @PageableDefault(size = 20, sort = "jerseyNumber") Pageable pageable) {
        return ResponseEntity.ok(playerPagedAssembler.toModel(service.findPlayers(id, pageable), playerAssembler));
    }

    @GetMapping("/{id}/sponsors")
    @Operation(summary = "Listar patrocinadores do time (paginado)", description = "Navegação pelo relacionamento Many-to-Many.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de patrocinadores"),
            @ApiResponse(responseCode = "404", description = "Time não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<PagedModel<EntityModel<SponsorResponse>>> findSponsors(
            @Parameter(description = "ID do time", example = "1") @PathVariable Long id,
            @ParameterObject @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(sponsorPagedAssembler.toModel(service.findSponsors(id, pageable), sponsorAssembler));
    }

    @PutMapping("/{id}/sponsors/{sponsorId}")
    @Operation(summary = "Vincular patrocinador ao time", description = "Cria a associação Many-to-Many. Requer chave READ_WRITE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vínculo criado; retorna o time atualizado"),
            @ApiResponse(responseCode = "404", description = "Time ou patrocinador não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Vínculo já existe",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<TeamResponse>> addSponsor(
            @Parameter(description = "ID do time", example = "1") @PathVariable Long id,
            @Parameter(description = "ID do patrocinador", example = "3") @PathVariable Long sponsorId) {
        return ResponseEntity.ok(assembler.toModel(service.addSponsor(id, sponsorId)));
    }

    @DeleteMapping("/{id}/sponsors/{sponsorId}")
    @Operation(summary = "Desvincular patrocinador do time", description = "Remove a associação Many-to-Many. Requer chave ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vínculo removido"),
            @ApiResponse(responseCode = "404", description = "Time, patrocinador ou vínculo não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> removeSponsor(
            @Parameter(description = "ID do time", example = "1") @PathVariable Long id,
            @Parameter(description = "ID do patrocinador", example = "1") @PathVariable Long sponsorId) {
        service.removeSponsor(id, sponsorId);
        return ResponseEntity.noContent().build();
    }
}
