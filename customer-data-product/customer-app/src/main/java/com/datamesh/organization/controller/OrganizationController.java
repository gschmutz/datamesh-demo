package com.datamesh.organization.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.datamesh.organization.dto.OrganizationDTO;
import com.datamesh.organization.entity.Organization;
import com.datamesh.organization.service.OrganizationService;

import java.net.URI;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping("{id}")
    public ResponseEntity<Organization> getOrganizationById(@PathVariable String id) {
        return ResponseEntity.of(organizationService.getOrganization(id));
    }

    @PostMapping
    public ResponseEntity<?> createOrganization(@RequestBody OrganizationDTO organization) {
        String id = organizationService.createOrganization(organization);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(location).build();
    }
}
