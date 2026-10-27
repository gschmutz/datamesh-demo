package com.datamesh.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.datamesh.organization.entity.Organization;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
}
