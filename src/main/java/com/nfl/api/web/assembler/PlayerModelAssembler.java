package com.nfl.api.web.assembler;

import com.nfl.api.dto.PlayerDtos.PlayerResponse;
import com.nfl.api.dto.PlayerDtos.PlayerResponseV2;
import com.nfl.api.web.controller.PlayerController;
import com.nfl.api.web.controller.TeamController;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class PlayerModelAssembler implements RepresentationModelAssembler<PlayerResponse, EntityModel<PlayerResponse>> {

    @Override
    public EntityModel<PlayerResponse> toModel(PlayerResponse player) {
        return EntityModel.of(player, links(player.id(), player.teamId()));
    }

    public EntityModel<PlayerResponseV2> toModelV2(PlayerResponseV2 player) {
        return EntityModel.of(player, links(player.id(), player.team() != null ? player.team().id() : null));
    }

    private List<Link> links(Long id, Long teamId) {
        List<Link> links = new ArrayList<>(List.of(
                linkTo(methodOn(PlayerController.class).findById(id)).withSelfRel(),
                linkTo(methodOn(PlayerController.class).update(id, null)).withRel("update"),
                linkTo(methodOn(PlayerController.class).delete(id)).withRel("delete"),
                linkTo(methodOn(PlayerController.class).findAll(null)).withRel("players")));
        if (teamId != null) {
            links.add(linkTo(methodOn(TeamController.class).findById(teamId)).withRel("team"));
            links.add(linkTo(methodOn(TeamController.class).findPlayers(teamId, null)).withRel("teammates"));
        }
        return links;
    }
}
