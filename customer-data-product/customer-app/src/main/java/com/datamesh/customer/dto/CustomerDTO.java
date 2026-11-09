package com.datamesh.customer.dto;

import java.time.LocalDate;
import java.util.List;

public record CustomerDTO(String id, String login, boolean active, boolean verified, boolean vip, String firstName, String lastName, String emailAddress, String preferredContact, Integer segmentId, LocalDate dateOfBirth, String preferredLanguage, boolean marketingConsent, String lifecycleStatus, List<AddressDTO> addresses, List<PhoneDTO> phones) {
}

