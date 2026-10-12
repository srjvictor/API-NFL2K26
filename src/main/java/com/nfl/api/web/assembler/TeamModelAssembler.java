package com.nfl.api.web.assembler;

import com.nfl.api.dto.TeamDtos.TeamResponse;
import com.nfl.api.web.controller.GameController;
import com.nfl.api.web.controller.StadiumController;
import com.nfl.api.web.controller.TeamController;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class TeamModelAssembler implements RepresentationModelAssembler<TeamResponse, EntityModel<TeamResponse>> {

    @Override
    public EntityModel<TeamResponse> toModel(TeamResponse team) {
        Long id = team.id();
        EntityModel<TeamResponse> model = EntityModel.of(team,
                linkTo(methodOn(TeamController.class).findById(id)).withSelfRel(),
                linkTo(methodOn(TeamController.class).update(id, null)).withRel("update"),
                linkTo(methodOn(TeamController.class).delete(id)).withRel("delete"),
                linkTo(methodOn(TeamController.class).findAll(null)).withRel("teams"),
                linkTo(methodOn(TeamController.class).findPlayers(id, null)).withRel("players"),
                linkTo(methodOn(TeamController.class).findSponsors(id, null)).withRel("sponsors"),
                linkTo(methodOn(GameController.class).findByTeam(id, null)).withRel("games"));
        if (team.stadiumId() != null) {
            model.add(linkTo(methodOn(StadiumController.class).findById(team.stadiumId())).withRel("stadium"));
        }
        return model;
    }
}
