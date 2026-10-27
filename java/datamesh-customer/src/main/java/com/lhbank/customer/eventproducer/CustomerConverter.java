package com.lhbank.customer.eventproducer;

import java.util.List;

import com.lhbank.customer.avro.CustomerState;
import com.lhbank.customer.entity.Person;
import com.lhbank.customer.entity.Address;

public class CustomerConverter {

    private static com.lhbank.customer.avro.Address convertAddress(Address address) {
        return com.lhbank.customer.avro.Address.newBuilder()
                .setStreet(address.getStreet())
                .setZipCode(address.getZipCode())
                .setCity(address.getCity())
                .setState(address.getState())
                .build();
    }

    public static CustomerState convert(Person person) {
        List<com.lhbank.customer.avro.Address> addresses = person.getAddresses().stream()
                .map(CustomerConverter::convertAddress)
                .collect(java.util.stream.Collectors.toCollection(() -> new java.util.ArrayList<>()));

        return CustomerState.newBuilder()
                .setCustomer(com.lhbank.customer.avro.Customer.newBuilder()
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
