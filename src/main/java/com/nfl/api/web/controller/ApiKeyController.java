package com.nfl.api.web.controller;

import com.nfl.api.domain.ApiKey;
import com.nfl.api.domain.enums.ApiKeyRole;
import com.nfl.api.dto.ApiKeyDtos.*;
import com.nfl.api.exception.ApiError;
import com.nfl.api.service.ApiKeyService;
import com.nfl.api.web.assembler.ApiKeyModelAssembler;
import com.nfl.api.web.filter.ApiKeyAuthFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/keys")
@Tag(name = "Chaves de API", description = "Geração e gerenciamento de chaves (X-API-Key). "
        + "O auto-cadastro é público; as demais operações exigem chave ADMIN, exceto /me.")
public class ApiKeyController {

    private final ApiKeyService service;
    private final ApiKeyModelAssembler assembler;
    private final PagedResourcesAssembler<ApiKeyResponse> pagedAssembler;

    public ApiKeyController(ApiKeyService service, ApiKeyModelAssembler assembler,
                            PagedResourcesAssembler<ApiKeyResponse> pagedAssembler) {
        this.service = service;
        this.assembler = assembler;
        this.pagedAssembler = pagedAssembler;
    }

    @PostMapping("/register")
    @SecurityRequirements // endpoint público: sem X-API-Key
    @Operation(summary = "Auto-cadastro: gerar chave READ_ONLY",
            description = "Endpoint público. Gera uma chave com permissão apenas de leitura. "
                    + "O valor da chave é exibido somente nesta resposta. Requer X-Idempotency-Key.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Chave gerada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<ApiKeyCreatedResponse>> register(@Valid @RequestBody ApiKeyRegisterRequest request) {
        return created(service.generate(request.owner(), request.email(), ApiKeyRole.READ_ONLY));
    }

    @PostMapping
    @Operation(summary = "Gerar chave com nível de acesso (ADMIN)",
            description = "Gera uma chave READ_ONLY, READ_WRITE ou ADMIN. Requer chave ADMIN e X-Idempotency-Key.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Chave gerada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<ApiKeyCreatedResponse>> create(@Valid @RequestBody ApiKeyCreateRequest request) {
        return created(service.generate(request.owner(), request.email(), request.role()));
    }

    @GetMapping
    @Operation(summary = "Listar chaves (paginado, ADMIN)", description = "Filtro opcional por trecho do nome do responsável.")
    @ApiResponse(responseCode = "200", description = "Página de chaves (sem o valor secreto)")
    public ResponseEntity<PagedModel<EntityModel<ApiKeyResponse>>> findAll(
            @Parameter(description = "Trecho do nome do responsável", example = "admin") @RequestParam(required = false) String owner,
            @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(pagedAssembler.toModel(service.findAll(owner, pageable), assembler));
    }

    @GetMapping("/me")
    @Operation(summary = "Dados da chave em uso", description = "Retorna os metadados da chave enviada em X-API-Key (qualquer nível).")
    @ApiResponse(responseCode = "200", description = "Metadados da chave atual")
    public ResponseEntity<EntityModel<ApiKeyResponse>> me(HttpServletRequest request) {
        ApiKey current = currentKey(request);
        return ResponseEntity.ok(EntityModel.of(ApiKeyResponse.from(current),
                linkTo(methodOn(ApiKeyController.class).me(null)).withSelfRel()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar chave por ID (ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chave encontrada"),
            @ApiResponse(responseCode = "404", description = "Chave não encontrada",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<ApiKeyResponse>> findById(
            @Parameter(description = "ID da chave", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(assembler.toModel(service.findById(id)));
    }

    @PatchMapping("/{id}/revoke")
    @Operation(summary = "Revogar chave (ADMIN)", description = "Desativa a chave; requisições com ela passam a receber 401.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chave revogada"),
            @ApiResponse(responseCode = "404", description = "Chave não encontrada",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "Tentativa de revogar a própria chave",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<EntityModel<ApiKeyResponse>> revoke(
            @Parameter(description = "ID da chave", example = "2") @PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(assembler.toModel(service.revoke(id, currentKeyId(request))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir chave (ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Chave excluída"),
            @ApiResponse(responseCode = "404", description = "Chave não encontrada",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "Tentativa de excluir a própria chave",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID da chave", example = "2") @PathVariable Long id, HttpServletRequest request) {
        service.delete(id, currentKeyId(request));
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<EntityModel<ApiKeyCreatedResponse>> created(ApiKeyCreatedResponse body) {
        EntityModel<ApiKeyCreatedResponse> model = EntityModel.of(body,
                linkTo(methodOn(ApiKeyController.class).findById(body.id())).withSelfRel(),
                linkTo(methodOn(ApiKeyController.class).me(null)).withRel("me"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(model.getRequiredLink("self").toUri())
                .body(model);
    }

    private static ApiKey currentKey(HttpServletRequest request) {
        return (ApiKey) request.getAttribute(ApiKeyAuthFilter.CURRENT_API_KEY);
    }

    private static Long currentKeyId(HttpServletRequest request) {
        ApiKey key = currentKey(request);
        return key != null ? key.getId() : null;
    }
}
