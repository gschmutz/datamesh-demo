package com.datamesh.organization.dto;

import java.util.List;

public record OrganizationDTO(String id, String name, boolean shared, String domain, boolean domainAssignment, boolean active, boolean vip, String note, List<Long> memberIds, List<Long> secondaryMemberIds) {
}
