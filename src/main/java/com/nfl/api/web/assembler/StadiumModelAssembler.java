package com.nfl.api.web.assembler;

import com.nfl.api.dto.StadiumDtos.StadiumResponse;
import com.nfl.api.web.controller.StadiumController;
import com.nfl.api.web.controller.TeamController;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class StadiumModelAssembler implements RepresentationModelAssembler<StadiumResponse, EntityModel<StadiumResponse>> {

    @Override
    public EntityModel<StadiumResponse> toModel(StadiumResponse stadium) {
        Long id = stadium.id();
        EntityModel<StadiumResponse> model = EntityModel.of(stadium,
                linkTo(methodOn(StadiumController.class).findById(id)).withSelfRel(),
                linkTo(methodOn(StadiumController.class).update(id, null)).withRel("update"),
                linkTo(methodOn(StadiumController.class).delete(id)).withRel("delete"),
                linkTo(methodOn(StadiumController.class).findAll(null)).withRel("stadiums"),
                linkTo(methodOn(StadiumController.class).findGames(id, null)).withRel("games"));
        if (stadium.teamId() != null) {
            model.add(linkTo(methodOn(TeamController.class).findById(stadium.teamId())).withRel("team"));
        }
        return model;
    }
}
