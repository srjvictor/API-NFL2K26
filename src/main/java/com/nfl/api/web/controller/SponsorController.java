package com.nfl.api.web.controller;

import com.nfl.api.dto.SponsorDtos.SponsorRequest;
import com.nfl.api.dto.SponsorDtos.SponsorResponse;
import com.nfl.api.dto.TeamDtos.TeamResponse;
import com.nfl.api.exception.ApiError;
import com.nfl.api.service.SponsorService;
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
import jakarta.validation.constraints.PositiveOrZero;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/sponsors")
@Tag(name = "Patrocinadores", description = "Patrocinadores — CRUD, busca por setor/valor e times patrocinados (Many-to-Many)")
public class SponsorController {

    private final SponsorService service;
    private final SponsorModelAssembler assembler;
    private final TeamModelAssembler teamAssembler;
    private final PagedResourcesAssembler<SponsorResponse> pagedAssembler;
    private final PagedResourcesAssembler<TeamResponse> teamPagedAssembler;

    public SponsorController(SponsorService service, SponsorModelAssembler assembler,
                             TeamModelAssembler teamAssembler,
                             PagedResourcesAssembler<SponsorResponse> pagedAssembler,
                             PagedResourcesAssembler<TeamResponse> teamPagedAssembler) {
        this.service = service;
        this.assembler = assembler;
        this.teamAssembler = teamAssembler;
        this.pagedAssembler = pagedAssembler;
        this.teamPagedAssembler = teamPagedAssembler;
    }

    @GetMapping
    @Operation(summary = "Listar patrocinadores (paginado)", description = "Retorna uma página de patrocinadores com links HATEOAS.")
    @ApiResponse(responseCode = "200", description = "Página de patrocinadores")
    public ResponseEntity<PagedModel<EntityModel<SponsorResponse>>> findAll(
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(pagedAssembler.toModel(service.findAll(pageable), assembler));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar patrocinador por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Patrocinador encontrado"),
            @ApiResponse(responseCode = "404", description = "Patrocinador não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<SponsorResponse>> findById(
            @Parameter(description = "ID do patrocinador", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(assembler.toModel(service.findById(id)));
    }

    @GetMapping("/search")
    @Operation(summary = "Buscar patrocinadores por setor e valor mínimo",
            description = "Consulta personalizada (JPQL). Exemplo: /api/sponsors/search?industry=bebida&minValue=50000000")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página com os patrocinadores encontrados"),
            @ApiResponse(responseCode = "400", description = "Parâmetros inválidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<PagedModel<EntityModel<SponsorResponse>>> search(
            @Parameter(description = "Trecho do setor de atuação", example = "bebida") @RequestParam(required = false) String industry,
            @Parameter(description = "Valor anual mínimo (USD)", example = "50000000")
            @RequestParam(required = false) @PositiveOrZero(message = "O valor mínimo não pode ser negativo") BigDecimal minValue,
            @ParameterObject @PageableDefault(size = 10, sort = "annualValueUsd", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(pagedAssembler.toModel(service.search(industry, minValue, pageable), assembler));
    }

    @GetMapping("/{id}/teams")
    @Operation(summary = "Listar times patrocinados (paginado)", description = "Navegação pelo lado inverso do Many-to-Many.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de times"),
            @ApiResponse(responseCode = "404", description = "Patrocinador não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<PagedModel<EntityModel<TeamResponse>>> findTeams(
            @Parameter(description = "ID do patrocinador", example = "1") @PathVariable Long id,
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(teamPagedAssembler.toModel(service.findTeams(id, pageable), teamAssembler));
    }

    @PostMapping
    @Operation(summary = "Cadastrar patrocinador",
            description = "Cria um patrocinador. Requer chave READ_WRITE e o header X-Idempotency-Key.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(name = "Pepsi", value = """
                            {"name":"Pepsi","industry":"Bebidas","annualValueUsd":100000000.00,"website":"https://www.pepsi.com"}"""))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Patrocinador criado"),
            @ApiResponse(responseCode = "409", description = "Nome duplicado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<SponsorResponse>> create(@Valid @RequestBody SponsorRequest request) {
        EntityModel<SponsorResponse> model = assembler.toModel(service.create(request));
        return ResponseEntity.created(model.getRequiredLink("self").toUri()).body(model);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar patrocinador", description = "Substitui os dados do patrocinador. Requer chave READ_WRITE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Patrocinador atualizado"),
            @ApiResponse(responseCode = "404", description = "Patrocinador não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Nome duplicado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<SponsorResponse>> update(
            @Parameter(description = "ID do patrocinador", example = "1") @PathVariable Long id,
            @Valid @RequestBody SponsorRequest request) {
        return ResponseEntity.ok(assembler.toModel(service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir patrocinador", description = "Remove o patrocinador e seus vínculos com times. Requer chave ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Patrocinador excluído"),
            @ApiResponse(responseCode = "404", description = "Patrocinador não encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> delete(@Parameter(description = "ID do patrocinador", example = "1") @PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
