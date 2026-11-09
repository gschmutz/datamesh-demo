package com.datamesh.customer.eventproducer;

import java.util.List;

import com.datamesh.customer.avro.CustomerState;
import com.datamesh.customer.entity.Person;
import com.datamesh.customer.entity.Address;
import com.datamesh.customer.entity.Phone;

public class CustomerConverter {

    private static com.datamesh.customer.avro.Address convertAddress(Address address) {
        return com.datamesh.customer.avro.Address.newBuilder()
                .setStreet(address.getStreet())
                .setZipCode(address.getZipCode())
                .setCity(address.getCity())
                .setState(address.getState())
                .setCountry(address.getCountry() == null ? null : address.getCountry().getCode())
                .setType(address.getType() == null ? null : address.getType().getName())
                .build();
    }

    private static com.datamesh.customer.avro.Phone convertPhone(Phone phone) {
        return com.datamesh.customer.avro.Phone.newBuilder()
                .setNumber(phone.getNumber())
                .setType(phone.getType() == null ? null : phone.getType().getName())
                .setAvailability(phone.getAvailability())
                .build();
    }

    public static CustomerState convert(Person person) {
        List<com.datamesh.customer.avro.Address> addresses = person.getAddresses().stream()
                .map(CustomerConverter::convertAddress)
                .collect(java.util.stream.Collectors.toCollection(() -> new java.util.ArrayList<>()));

        List<com.datamesh.customer.avro.Phone> phones = person.getPhones().stream()
                .map(CustomerConverter::convertPhone)
                .collect(java.util.stream.Collectors.toCollection(() -> new java.util.ArrayList<>()));

        return CustomerState.newBuilder()
                .setCustomer(com.datamesh.customer.avro.Customer.newBuilder()
                        .setId(person.getId())
                        .setLogin(person.getLogin())
                        .setActive(person.isActive())
                        .setVerified(person.isVerified())
                        .setVip(person.isVip())
                        .setFirstName(person.getFirstName())
                        .setLastName(person.getLastName())
                        .setEmailAddress(person.getEmailAddress())
                        .setPreferredContact(person.getPreferredContact())
                        .setSegment(person.getSegment() == null ? null : person.getSegment().getName())
                        .setDateOfBirth(person.getDateOfBirth())
                        .setPreferredLanguage(person.getPreferredLanguage())
                        .setMarketingConsent(person.isMarketingConsent())
                        .setLifecycleStatus(person.getLifecycleStatus())
                        .setAddresses(addresses)
                        .setPhones(phones)
                        .setOnboardedDate(person.getOnboardedDate())
                        .build())
                .build();
    }
}
