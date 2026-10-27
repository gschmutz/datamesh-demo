package com.datamesh.customer.dto;

import java.util.List;

public record CustomerDTO(String id, String firstName, String lastName, String emailAddress, String phoneNumber, String preferredContact, String segment, Long avgTransactionAmount, List<AddressDTO> addresses) {
}

