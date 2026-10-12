package com.nfl.api.web.assembler;

import com.nfl.api.domain.enums.GameStatus;
import com.nfl.api.dto.GameDtos.GameResponse;
import com.nfl.api.web.controller.GameController;
import com.nfl.api.web.controller.StadiumController;
import com.nfl.api.web.controller.TeamController;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class GameModelAssembler implements RepresentationModelAssembler<GameResponse, EntityModel<GameResponse>> {

    @Override
    public EntityModel<GameResponse> toModel(GameResponse game) {
        Long id = game.id();
        EntityModel<GameResponse> model = EntityModel.of(game,
                linkTo(methodOn(GameController.class).findById(id)).withSelfRel(),
                linkTo(methodOn(GameController.class).update(id, null)).withRel("update"),
                linkTo(methodOn(GameController.class).delete(id)).withRel("delete"),
                linkTo(methodOn(GameController.class).findAll(null)).withRel("games"),
                linkTo(methodOn(TeamController.class).findById(game.homeTeamId())).withRel("homeTeam"),
                linkTo(methodOn(TeamController.class).findById(game.awayTeamId())).withRel("awayTeam"));
        // Link condicional: o placar só pode ser atualizado em jogos que ainda não foram cancelados
        if (game.status() != GameStatus.CANCELED) {
            model.add(linkTo(methodOn(GameController.class).updateScore(id, null)).withRel("updateScore"));
        }
        if (game.stadiumId() != null) {
            model.add(linkTo(methodOn(StadiumController.class).findById(game.stadiumId())).withRel("stadium"));
        }
        return model;
    }
}
