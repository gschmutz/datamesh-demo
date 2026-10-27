package com.datamesh.organization.eventproducer;

import com.datamesh.organization.avro.OrganizationState;
import com.datamesh.organization.entity.Organization;

public class OrganizationConverter {

    public static OrganizationState convert(Organization organization) {
        return OrganizationState.newBuilder()
                .setOrganization(com.datamesh.organization.avro.Organization.newBuilder()
                        .setId(organization.getId())
                        .setName(organization.getName())
                        .setShared(organization.isShared())
                        .setDomain(organization.getDomain())
                        .setDomainAssignment(organization.isDomainAssignment())
                        .setActive(organization.isActive())
                        .setVip(organization.isVip())
                        .setNote(organization.getNote())
                        .setMemberIds(organization.getMemberIds())
                        .setSecondaryMemberIds(organization.getSecondaryMemberIds())
                        .setCreatedAt(organization.getCreatedAt().toString())
                        .setUpdatedAt(organization.getModifiedAt().toString())
                        .build())
                .build();
    }
}
