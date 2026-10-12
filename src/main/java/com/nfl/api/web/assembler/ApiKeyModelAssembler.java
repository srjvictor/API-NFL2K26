package com.nfl.api.web.assembler;

import com.nfl.api.dto.ApiKeyDtos.ApiKeyResponse;
import com.nfl.api.web.controller.ApiKeyController;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class ApiKeyModelAssembler implements RepresentationModelAssembler<ApiKeyResponse, EntityModel<ApiKeyResponse>> {

    @Override
    public EntityModel<ApiKeyResponse> toModel(ApiKeyResponse key) {
        Long id = key.id();
        EntityModel<ApiKeyResponse> model = EntityModel.of(key,
                linkTo(methodOn(ApiKeyController.class).findById(id)).withSelfRel(),
                linkTo(methodOn(ApiKeyController.class).delete(id, null)).withRel("delete"),
                linkTo(methodOn(ApiKeyController.class).findAll(null, null)).withRel("apiKeys"));
        if (key.active()) {
            model.add(linkTo(methodOn(ApiKeyController.class).revoke(id, null)).withRel("revoke"));
        }
        return model;
    }
}
