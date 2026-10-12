package com.nfl.api.web.assembler;

import com.nfl.api.dto.SponsorDtos.SponsorResponse;
import com.nfl.api.web.controller.SponsorController;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class SponsorModelAssembler implements RepresentationModelAssembler<SponsorResponse, EntityModel<SponsorResponse>> {

    @Override
    public EntityModel<SponsorResponse> toModel(SponsorResponse sponsor) {
        Long id = sponsor.id();
        return EntityModel.of(sponsor,
                linkTo(methodOn(SponsorController.class).findById(id)).withSelfRel(),
                linkTo(methodOn(SponsorController.class).update(id, null)).withRel("update"),
                linkTo(methodOn(SponsorController.class).delete(id)).withRel("delete"),
                linkTo(methodOn(SponsorController.class).findAll(null)).withRel("sponsors"),
                linkTo(methodOn(SponsorController.class).findTeams(id, null)).withRel("teams"));
    }
}
