package com.datamesh.customer.dto;

import java.util.List;

public record CustomerDTO(String id, String login, boolean active, boolean verified, boolean vip, String firstName, String lastName, String emailAddress, String phoneNumber, String preferredContact, String segment, Long organizationId, List<AddressDTO> addresses) {
}

