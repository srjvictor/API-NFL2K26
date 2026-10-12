package com.nfl.api.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/** Ponto de entrada HATEOAS: a partir daqui o cliente navega por toda a API seguindo links. */
@RestController
@RequestMapping("/api")
@Tag(name = "Raiz", description = "Ponto de entrada da API com links para todos os recursos")
public class RootController {

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Índice da API", description = "Endpoint público com links para todas as coleções.")
    @ApiResponse(responseCode = "200", description = "Links de navegação")
    public ResponseEntity<RepresentationModel<?>> root() {
        RepresentationModel<?> model = new RepresentationModel<>();
        model.add(linkTo(methodOn(RootController.class).root()).withSelfRel());
        model.add(linkTo(methodOn(TeamController.class).findAll(null)).withRel("teams"));
        model.add(linkTo(methodOn(PlayerController.class).findAll(null)).withRel("players"));
        model.add(linkTo(methodOn(StadiumController.class).findAll(null)).withRel("stadiums"));
        model.add(linkTo(methodOn(SponsorController.class).findAll(null)).withRel("sponsors"));
        model.add(linkTo(methodOn(GameController.class).findAll(null)).withRel("games"));
        model.add(linkTo(methodOn(ApiKeyController.class).register(null)).withRel("register-api-key"));
        model.add(Link.of("/swagger-ui.html", "documentation"));
        return ResponseEntity.ok(model);
    }
}
