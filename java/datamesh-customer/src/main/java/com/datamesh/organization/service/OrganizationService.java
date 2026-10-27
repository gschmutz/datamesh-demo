package com.datamesh.organization.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.datamesh.organization.dto.OrganizationDTO;
import com.datamesh.organization.entity.Organization;
import com.datamesh.organization.eventproducer.OrganizationStateProducer;
import com.datamesh.organization.repository.OrganizationRepository;

import java.util.ArrayList;
import java.util.Optional;

@Service
public class OrganizationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizationRepository;
    private final OrganizationStateProducer organizationStateProducer;

    public OrganizationService(OrganizationRepository organizationRepository, OrganizationStateProducer organizationStateProducer) {
        this.organizationRepository = organizationRepository;
        this.organizationStateProducer = organizationStateProducer;
    }

    /**
     * Get the organization by id
     *
     * @param id The organization id
     * @return The organization if exists
     */
    public Optional<Organization> getOrganization(String id) {
        LOGGER.info("getOrganization({})", id);

        return organizationRepository.findById(id);
    }

    /**
     * Create (or update) an organization, given by the organizationDTO parameter.
     *
     * @param organizationDTO the organization to save
     * @return the id of the organization
     */
    public String createOrganization(OrganizationDTO organizationDTO) {
        LOGGER.info("createOrganization({})", organizationDTO);

        var organization = new Organization();
        organization.setId(organizationDTO.id());
        organization.setName(organizationDTO.name());
        organization.setShared(organizationDTO.shared());
        organization.setDomain(organizationDTO.domain() != null ? organizationDTO.domain() : "");
        organization.setDomainAssignment(organizationDTO.domainAssignment());
        organization.setActive(organizationDTO.active());
        organization.setVip(organizationDTO.vip());
        organization.setNote(organizationDTO.note());
        organization.setMemberIds(organizationDTO.memberIds() != null ? new ArrayList<>(organizationDTO.memberIds()) : new ArrayList<>());
        organization.setSecondaryMemberIds(organizationDTO.secondaryMemberIds() != null ? new ArrayList<>(organizationDTO.secondaryMemberIds()) : new ArrayList<>());

        // use the returned instance, it carries the audit timestamps
        var saved = organizationRepository.saveAndFlush(organization);

        // Publish the event
        organizationStateProducer.send(saved);

        return saved.getId();
    }
}
