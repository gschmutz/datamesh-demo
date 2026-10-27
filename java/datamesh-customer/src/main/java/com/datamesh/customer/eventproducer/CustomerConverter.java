package com.datamesh.customer.eventproducer;

import java.util.List;

import com.datamesh.customer.avro.CustomerState;
import com.datamesh.customer.entity.Person;
import com.datamesh.customer.entity.Address;

public class CustomerConverter {

    private static com.datamesh.customer.avro.Address convertAddress(Address address) {
        return com.datamesh.customer.avro.Address.newBuilder()
                .setStreet(address.getStreet())
                .setZipCode(address.getZipCode())
                .setCity(address.getCity())
                .setState(address.getState())
                .build();
    }

    public static CustomerState convert(Person person) {
        List<com.datamesh.customer.avro.Address> addresses = person.getAddresses().stream()
                .map(CustomerConverter::convertAddress)
                .collect(java.util.stream.Collectors.toCollection(() -> new java.util.ArrayList<>()));

        return CustomerState.newBuilder()
                .setCustomer(com.datamesh.customer.avro.Customer.newBuilder()
                        .setId(person.getId())
                        .setFirstName(person.getFirstName())
                        .setLastName(person.getLastName())
                        .setEmailAddress(person.getEmailAddress())
                        .setPhoneNumber(person.getPhoneNumber())
                        .setPreferredContact(person.getPreferredContact())
                        .setSegment(person.getSegment())
                        .setAvgTransactionAmount(person.getAvgTransactionalAmount())
                        .setAddresses(addresses)
                        .setOnboardedDate(person.getOnboardedDate())
                        .build())
                .build();
    }
}
